package com.bank.transfer.integration;

import com.bank.transfer.dto.AccountTransferDto;
import com.bank.transfer.dto.CardTransferDto;
import com.bank.transfer.dto.PhoneTransferDto;
import com.bank.transfer.outbox.OutboxEvent;
import com.bank.transfer.outbox.OutboxRepository;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;
import com.bank.transfer.service.TransferService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Интеграционные тесты transfer-service.
 */
 /**
 * Проверяем полный цикл:
 * saveXxxTransfer() → entity в БД + outbox_events → Kafka топик
 */
 /**
 * Без Testcontainers нельзя:
 * - Проверить реальную Liquibase-миграцию transfer + outbox таблиц
 * - Убедиться, что outbox запись создаётся в той же транзакции, что и перевод
 * - Проверить что OutboxRelayScheduler реально отправляет в Kafka
 */
@DisplayName("Transfer Service — Integration Tests")
class TransferServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TransferService              transferService;
    @Autowired private AccountTransferRepository   accountTransferRepository;
    @Autowired private CardTransferRepository      cardTransferRepository;
    @Autowired private PhoneTransferRepository     phoneTransferRepository;
    @Autowired private OutboxRepository            outboxRepository;

    private KafkaConsumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        accountTransferRepository.deleteAll();
        cardTransferRepository.deleteAll();
        phoneTransferRepository.deleteAll();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(List.of("suspicious-transfers.create"));
    }

    @AfterEach
    void tearDown() {
        kafkaConsumer.close();
    }

    // ── Тест 1: Account transfer ─────────────────────────────────────────────

    @Test
    @DisplayName("saveAccountTransfer: сохраняет перевод в БД и создаёт Outbox-запись")
    void saveAccountTransfer_persistsToDbAndOutbox() {
        AccountTransferDto dto = new AccountTransferDto();
        dto.setAccountNumber("40817810099910001234");
        dto.setAmount(new BigDecimal("25000.00"));
        dto.setPurpose("Оплата аренды");
        dto.setAccountDetailsId(1L);

        transferService.saveAccountTransfer(dto);

        assertThat(accountTransferRepository.count()).isEqualTo(1);

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("suspicious-transfers.create");
        assertThat(events.get(0).getEventType()).isEqualTo("AccountTransferCreated");
        assertThat(events.get(0).getSentAt()).isNull();
    }

    // ── Тест 2: Card transfer ────────────────────────────────────────────────

    @Test
    @DisplayName("saveCardTransfer: сохраняет перевод и публикует в Kafka через Outbox")
    void saveCardTransfer_eventReachesKafka() {
        CardTransferDto dto = new CardTransferDto();
        // Исправлено: String вместо long (поле cardNumber — String после рефакторинга)
        dto.setCardNumber("4111111111111111");
        dto.setAmount(new BigDecimal("3500.00"));
        dto.setPurpose("Перевод по карте");

        transferService.saveCardTransfer(dto);

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        Long transferId = cardTransferRepository.findAll().iterator().next().getId();
        assertThat(events.get(0).getPartitionKey()).isEqualTo(String.valueOf(transferId));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(500));
            assertThat(records.isEmpty()).isFalse();
            assertThat(records.iterator().next().key()).isEqualTo(String.valueOf(transferId));
        });
    }

    // ── Тест 3: Phone transfer ───────────────────────────────────────────────

    @Test
    @DisplayName("savePhoneTransfer: сохраняет перевод и создаёт Outbox-запись")
    void savePhoneTransfer_persistsToDbAndOutbox() {
        PhoneTransferDto dto = new PhoneTransferDto();
        // Исправлено: String вместо long (поле phoneNumber — String после рефакторинга)
        dto.setPhoneNumber("79161234567");
        dto.setAmount(new BigDecimal("500.00"));
        dto.setPurpose("СБП");

        transferService.savePhoneTransfer(dto);

        assertThat(phoneTransferRepository.count()).isEqualTo(1);
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("PhoneTransferCreated");
    }

    // ── Тест 4: Атомарность DB + Outbox ─────────────────────────────────────

    @Test
    @DisplayName("Атомарность: нет перевода в БД — нет записи в outbox_events")
    void saveAccountTransfer_nullDto_noOutboxEntry() {
        try {
            transferService.saveAccountTransfer(null);
        } catch (Exception ignored) { /* ожидаемо */ }

        assertThat(accountTransferRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}
