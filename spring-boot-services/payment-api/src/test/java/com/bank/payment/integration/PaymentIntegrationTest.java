package com.bank.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.bank.payment.dto.CreatePaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.outbox.OutboxEvent;
import com.bank.payment.outbox.OutboxRepository;
import com.bank.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Интеграционные тесты payment-api.
 */
 /**
 * Тестируем полный цикл:
 * POST /api/v1/payments  →  payments таблица  →  outbox_events  →  Kafka-топик
 */
 /**
 * Зачем Testcontainers вместо Mockito:
 * - Уникальный индекс uk_client_idempotency — только реальный PostgreSQL его проверяет
 * - Liquibase-миграции проверяются на реальной БД
 * - OutboxRelayScheduler реально публикует в Kafka — проверяем at-least-once delivery
 * - Race condition DataIntegrityViolationException нельзя воспроизвести с Mockito
 */
 /**
 * Аутентификация: все запросы используют Bearer JWT токен.
 * clientId кодируется в claim «clientId» и извлекается сервером из JWT.
 */
@DisplayName("Payment API — Integration Tests")
class PaymentIntegrationTest extends AbstractIntegrationTest {

    // Тестовый секрет — совпадает с application-test.yml app.jwt.secret-key
    private static final String TEST_JWT_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzISE=";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> kafkaConsumer;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        outboxRepository.deleteAll();

        // Kafka consumer для проверки доставки событий
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(List.of("payment.created"));
    }

    @AfterEach
    void tearDown() {
        kafkaConsumer.close();
    }

    // ── Тест 1: Успешное создание платежа ───────────────────────────────────

    @Test
    @DisplayName("POST /payments — создаёт платёж, записывает в outbox, публикует в Kafka")
    void createPayment_success_writesToDbAndOutboxAndKafka() throws Exception {
        // Given
        String clientId = "CLIENT_001";
        CreatePaymentRequest request = new CreatePaymentRequest(
                "40817810099910004312",
                new BigDecimal("5000.00"),
                "RUB",
                "Оплата поставки"
        );

        // When — HTTP POST с JWT
        ResponseEntity<PaymentResponse> response = postPayment(clientId, request);

        // Then — HTTP ответ
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("CREATED");
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));

        Long paymentId = response.getBody().getId();

        // Then — запись в payments таблице
        assertThat(paymentRepository.findById(paymentId)).isPresent();

        // Then — запись в outbox_events (в той же транзакции)
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getTopic()).isEqualTo("payment.created");
        assertThat(outboxEvents.get(0).getPartitionKey()).isEqualTo(String.valueOf(paymentId));
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("PaymentCreated");
        assertThat(outboxEvents.get(0).getSentAt()).isNull(); // ещё не отправлено

        // Then — Kafka получает событие (OutboxRelayScheduler срабатывает за ~500ms)
        await().atMost(5, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(500));
            assertThat(records.isEmpty()).isFalse();

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo(String.valueOf(paymentId));

            Map<?, ?> payload = objectMapper.readValue(record.value(), Map.class);
            assertThat(payload.get("clientId")).isEqualTo(clientId);
            assertThat(payload.get("status")).isEqualTo("CREATED");
        });

        // Then — outbox_events помечена как отправленная
        await().atMost(5, SECONDS).untilAsserted(() -> {
            OutboxEvent event = outboxRepository.findAll().get(0);
            assertThat(event.getSentAt()).isNotNull();
        });
    }

    // ── Тест 2: Идемпотентность ──────────────────────────────────────────────

    @Test
    @DisplayName("Повторный POST с тем же idempotency-key возвращает существующий платёж без дублей в БД")
    void createPayment_duplicateIdempotencyKey_returnsExistingPayment() {
        String clientId = "CLIENT_002";
        CreatePaymentRequest request = new CreatePaymentRequest(
                "40817810099910004312",
                new BigDecimal("1000.00"),
                "USD",
                "Subscription"
        );

        // Два одинаковых запроса → сервер генерирует тот же ключ
        ResponseEntity<PaymentResponse> first  = postPayment(clientId, request);
        ResponseEntity<PaymentResponse> second = postPayment(clientId, request);

        // Оба вернули 2xx и одинаковый ID
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(first.getBody().getId()).isEqualTo(second.getBody().getId());

        // В БД ровно одна запись
        assertThat(paymentRepository.count()).isEqualTo(1);

        // В outbox ровно одно событие (второй запрос не создаёт Outbox-запись)
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    // ── Тест 3: Валидация суммы ──────────────────────────────────────────────

    @Test
    @DisplayName("POST с отрицательной суммой возвращает 400 Bad Request")
    void createPayment_negativeAmount_returns400() {
        CreatePaymentRequest bad = new CreatePaymentRequest(
                "40817810099910004312",
                new BigDecimal("-1.00"),
                "RUB",
                "Bad"
        );
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments",
                org.springframework.http.HttpMethod.POST,
                buildRequest("CLIENT_X", bad),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(paymentRepository.count()).isZero();
    }

    // ── Тест 4: Уникальный индекс на уровне БД ───────────────────────────────

    @Test
    @DisplayName("Уникальный индекс uk_client_idempotency существует в PostgreSQL")
    void uniqueIndex_clientIdAndIdempotencyKey_existsInPostgres() {
        String clientId = "CLIENT_003";
        CreatePaymentRequest r1 = new CreatePaymentRequest(
                "40817810099910004312", new BigDecimal("100.00"), "RUB", "A");

        ResponseEntity<PaymentResponse> resp1 = postPayment(clientId, r1);
        ResponseEntity<PaymentResponse> resp2 = postPayment(clientId, r1);

        assertThat(resp1.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp1.getBody().getId()).isEqualTo(resp2.getBody().getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    // ── Тест 5: GET по idempotency key ──────────────────────────────────────

    @Test
    @DisplayName("GET /payments/by-key/{key} возвращает платёж по idempotency key")
    void getPaymentByKey_existingPayment_returnsCorrectData() {
        String clientId = "CLIENT_004";
        ResponseEntity<PaymentResponse> created = postPayment(clientId,
                new CreatePaymentRequest(
                        "40817810099910004312", new BigDecimal("777.00"), "EUR", "test"));
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();

        Long paymentId = created.getBody().getId();
        String key = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AssertionError("Payment not found: " + paymentId))
                .getIdempotencyKey();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + buildJwt(clientId));
        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/v1/payments/by-key/" + key,
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers),
                PaymentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAmount()).isEqualByComparingTo(new BigDecimal("777.00"));
        assertThat(response.getBody().getCurrency()).isEqualTo("EUR");
    }

    // ── Тест 6: Запрос без JWT → 401 ─────────────────────────────────────────

    @Test
    @DisplayName("POST без Authorization header → 401 Unauthorized")
    void createPayment_missingJwt_returns401() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "40817810099910004312", new BigDecimal("100.00"), "RUB", "test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Authorization header намеренно отсутствует

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(paymentRepository.count()).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<PaymentResponse> postPayment(String clientId,
                                                        CreatePaymentRequest body) {
        return restTemplate.exchange(
                "/api/v1/payments",
                org.springframework.http.HttpMethod.POST,
                buildRequest(clientId, body),
                PaymentResponse.class
        );
    }

    /** Строит HttpEntity с JSON-телом и JWT в заголовке Authorization. */
    private <T> HttpEntity<T> buildRequest(String clientId, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + buildJwt(clientId));
        return new HttpEntity<>(body, headers);
    }

 /**
     * Генерирует тестовый JWT с claim «clientId».
     * Секрет совпадает с app.jwt.secret-key из application-test.yml.
 */
    private String buildJwt(String clientId) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET));
        return Jwts.builder()
                .setSubject(clientId)
                .claim("clientId", clientId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
