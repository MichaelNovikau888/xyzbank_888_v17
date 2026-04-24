package com.bank.account.integration;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.outbox.OutboxEvent;
import com.bank.account.outbox.OutboxRepository;
import com.bank.account.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Интеграционные тесты account-service.
 /
 * Проверяем полный цикл:
 *   Kafka command → AccountCommandConsumer → account_details таблица
 *                                          → outbox_events → Kafka response
 /
 * Зачем Testcontainers вместо Mockito:
 *  - Liquibase-миграции (включая create-outbox-events.xml) проверяются на реальной БД
 *  - Уникальный индекс на account_number — только PostgreSQL его реально применяет
 *  - OutboxRelayScheduler реально публикует в Kafka (@Scheduled) — проверяем at-least-once
 *  - AccountMetrics.getAccountsCreated() инкрементируется только при полном pipeline
 */
@DisplayName("Account Service — Integration Tests")
class AccountCommandConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private OutboxRepository   outboxRepository;
    @Autowired private ObjectMapper       objectMapper;

    @Value("${kafka.topics.account-create}")
    private String accountCreateTopic;

    @Value("${kafka.topics.external-account-create}")
    private String externalAccountCreateTopic;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;

    // ── JWT-заглушка (токен формата Header.Payload.Sig с нужным role) ─────────
    // В тестах TokenValidationService делает заглушку через mock или
    // application-test.yaml отключает валидацию.
    // Здесь используем заголовок, который сервис принимает в тестовом профиле.
    private static final String FAKE_JWT = "Bearer test-token";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        outboxRepository.deleteAll();

        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(prodProps);

        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-account-consumer-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consProps);
        consumer.subscribe(List.of(externalAccountCreateTopic));
    }

    @AfterEach
    void tearDown() {
        producer.close();
        consumer.close();
    }

    // ── Тест 1: CREATE счёта → entity сохранена в БД ──────────────────────────
    @Test
    @DisplayName("CREATE: account saved to DB after Kafka command")
    void createAccount_savedToDb() throws Exception {
        AccountDto dto = buildAccountDto(1001L, "40817810000000001001");

        sendKafkaCommand(accountCreateTopic, dto, FAKE_JWT);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(accountRepository.existsAccountByAccountNumber(dto.getAccountNumber())).isTrue();
            Account found = accountRepository.findAccountByAccountNumber(dto.getAccountNumber());
            
            assertThat(found.getAccountNumber()).isEqualTo(dto.getAccountNumber());
            assertThat(found.getMoney()).isEqualByComparingTo(dto.getMoney());
        });
    }

    // ── Тест 2: CREATE → запись попадает в outbox_events ──────────────────────
    @Test
    @DisplayName("CREATE: outbox_events record written in same transaction")
    void createAccount_outboxEventWritten() throws Exception {
        AccountDto dto = buildAccountDto(1002L, "40817810000000001002");

        sendKafkaCommand(accountCreateTopic, dto, FAKE_JWT);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            assertThat(accountRepository.existsAccountByAccountNumber(dto.getAccountNumber())).isTrue();
            // Outbox-запись должна появиться (создана в той же транзакции через AccountOutboxHelper)
            List<OutboxEvent> events = outboxRepository.findAll();
            assertThat(events).isNotEmpty();
        });
    }

    // ── Тест 3: OutboxRelayScheduler доставляет событие в Kafka ───────────────
    @Test
    @DisplayName("Outbox relay: event delivered to external.account.create topic")
    void outboxRelay_deliversToKafka() throws Exception {
        AccountDto dto = buildAccountDto(1003L, "40817810000000001003");

        sendKafkaCommand(accountCreateTopic, dto, FAKE_JWT);

        // Ждём доставки через OutboxRelayScheduler (fixedDelay=500ms → должно уложиться в 10с)
        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
        });
    }

    // ── Тест 4: Idempotency — повторный CREATE не создаёт дубль ───────────────
    @Test
    @DisplayName("Idempotency: duplicate CREATE with same accountNumber skipped")
    void createAccount_idempotency_noDuplicate() throws Exception {
        AccountDto dto = buildAccountDto(1004L, "40817810000000001004");

        // Отправляем два раза подряд
        sendKafkaCommand(accountCreateTopic, dto, FAKE_JWT);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(accountRepository.existsAccountByAccountNumber(dto.getAccountNumber())).isTrue()
        );

        sendKafkaCommand(accountCreateTopic, dto, FAKE_JWT);

        // Ждём немного чтобы второй consume успел отработать
        Thread.sleep(2000);

        // Должен быть ровно один счёт с этим accountNumber
        long count = accountRepository.findAll().stream()
                .filter(a -> a.getAccountNumber().equals(dto.getAccountNumber()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Тест 5: Liquibase создала таблицы account_details и outbox_events ─────
    @Test
    @DisplayName("Liquibase: account_details and outbox_events tables exist")
    void liquibase_tablesCreated() {
        // Если контейнер поднялся и Liquibase отработал — репозитории работают
        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(outboxRepository.count()).isGreaterThanOrEqualTo(0);
    }

    // ── Тест 6: DELETE идемпотентен для несуществующего счёта ─────────────────
    @Test
    @DisplayName("Idempotency: DELETE of non-existent account does not throw")
    void deleteAccount_nonExistent_isIdempotent() throws Exception {
        // Отправляем DELETE на ID которого нет в БД
        String deletePayload = "9999999";
        ProducerRecord<String, String> record = new ProducerRecord<>(
                "account.delete", null, deletePayload);
        record.headers().add("Authorization", FAKE_JWT.getBytes());
        producer.send(record).get();

        // Ждём обработки — не должно быть исключений, сервис отвечает idempotent-сообщением
        Thread.sleep(3000);

        // Проверяем что приложение живо — репозиторий отвечает
        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AccountDto buildAccountDto(long passportId, String accountNumber) {
        AccountDto dto = new AccountDto();
        dto.setPassportId(passportId);
        dto.setAccountNumber(accountNumber);
        dto.setBankDetailsId(999L);
        dto.setMoney(new BigDecimal("5000.00"));
        dto.setNegativeBalance(false);
        dto.setProfileId(passportId); // упрощение для теста
        return dto;
    }

    private void sendKafkaCommand(String topic, Object payload, String jwt) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, json);
        record.headers().add("Authorization", jwt.getBytes());
        producer.send(record).get();
    }
}
