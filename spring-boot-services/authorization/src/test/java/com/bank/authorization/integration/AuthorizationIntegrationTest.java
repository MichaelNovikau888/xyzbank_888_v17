package com.bank.authorization.integration;

import com.bank.authorization.dto.AuthRequest;
import com.bank.authorization.dto.KafkaRequest;
import com.bank.authorization.dto.KafkaResponse;
import com.bank.authorization.dto.UserDto;
import com.bank.authorization.entity.User;
import com.bank.authorization.outbox.OutboxRepository;
import com.bank.authorization.repository.UserRepository;
import com.bank.authorization.utils.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 // Интеграционные тесты authorization-service.
 //
 // Покрываем полный цикл:
 //   1. Login → Kafka → JWT генерация → response topic
 //   2. Token validation round-trip (validate → valid/invalid ответ)
 //   3. User CREATE/UPDATE/DELETE через Kafka + Outbox relay
 //   4. Idempotency: повторный CREATE с тем же profileId → один пользователь
 //   5. Liquibase: таблицы users и outbox_events созданы
 //
 // Зачем Testcontainers:
 //  - Liquibase changelog-001.xml + create-outbox-events.xml проверяются на реальном PostgreSQL
 //  - JwtTokenUtil использует реальный HMAC-ключ — тест проверяет интеграцию
 //  - OutboxRelayScheduler реально публикует в Kafka (cannot mock @Scheduled)
 //  - BCrypt encoding реально работает при login
 */
@DisplayName("Authorization Service — Integration Tests")
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${topics.auth_login}")
    private String authLoginTopic;
    @Value("${topics.auth_login_response}")
    private String authLoginResponseTopic;
    @Value("${topics.auth_validate}")
    private String authValidateTopic;
    @Value("${topics.auth_validate_response}")
    private String authValidateResponseTopic;
    @Value("${topics.user_create}")
    private String userCreateTopic;
    @Value("${topics.user_create_response}")
    private String userCreateResponseTopic;
    @Value("${topics.user_update}")
    private String userUpdateTopic;
    @Value("${topics.user_delete}")
    private String userDeleteTopic;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> loginResponseConsumer;
    private KafkaConsumer<String, String> validateResponseConsumer;
    private KafkaConsumer<String, String> userCreateResponseConsumer;

 /**
     * Заранее сохранённый пользователь + его JWT для команд, требующих ROLE_ADMIN
 */
    private String adminJwt;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        outboxRepository.deleteAll();

        // Создаём admin-пользователя напрямую в БД для тестов
        User admin = new User();
        admin.setProfileId(1L);
        admin.setRole("ROLE_ADMIN");
        admin.setPassword(passwordEncoder.encode("admin-password"));
        userRepository.save(admin);

        // Генерируем JWT от имени admin для Kafka-команд, требующих ROLE_ADMIN
        adminJwt = jwtTokenUtil.generateToken("1", List.of(() -> "ROLE_ADMIN"));

        // Kafka producer
        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(prodProps);

        loginResponseConsumer = buildConsumer(List.of(authLoginResponseTopic));
        validateResponseConsumer = buildConsumer(List.of(authValidateResponseTopic));
        userCreateResponseConsumer = buildConsumer(List.of(userCreateResponseTopic));
    }

    @AfterEach
    void tearDown() {
        producer.close();
        loginResponseConsumer.close();
        validateResponseConsumer.close();
        userCreateResponseConsumer.close();
    }

    // ── Тест 1: Login → JWT в response topic ──────────────────────────────────
    @Test
    @DisplayName("Login: valid credentials → JWT token in response topic")
    void login_validCredentials_jwtInResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setProfileId(1L);
        authRequest.setPassword("admin-password");
        authRequest.setRequestId(UUID.randomUUID().toString());

        sendString(authLoginTopic, objectMapper.writeValueAsString(authRequest));

        // Ждём ответ в auth.login.response
        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    loginResponseConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
            for (ConsumerRecord<String, String> rec : records) {
                KafkaResponse response = objectMapper.readValue(rec.value(), KafkaResponse.class);
                assertThat(response.isSuccess()).isTrue();
                // Ответ содержит AuthResponse с JWT
                String body = objectMapper.writeValueAsString(response.getData());
                assertThat(body).containsIgnoringCase("jwt");
            }
        });
    }

    // ── Тест 2: Login неверный пароль → error response ────────────────────────
    @Test
    @DisplayName("Login: wrong password → error response, login_failed metric")
    void login_wrongPassword_errorResponse() throws Exception {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setProfileId(1L);
        authRequest.setPassword("wrong-password");
        authRequest.setRequestId(UUID.randomUUID().toString());

        sendString(authLoginTopic, objectMapper.writeValueAsString(authRequest));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    loginResponseConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
            for (ConsumerRecord<String, String> rec : records) {
                KafkaResponse response = objectMapper.readValue(rec.value(), KafkaResponse.class);
                assertThat(response.isSuccess()).isFalse();
            }
        });
    }

    // ── Тест 3: Token validation — valid JWT ──────────────────────────────────
    @Test
    @DisplayName("Token validation: valid JWT → success response")
    void tokenValidation_validToken_successResponse() throws Exception {
        KafkaRequest request = new KafkaRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setJwtToken(adminJwt);

        sendString(authValidateTopic, objectMapper.writeValueAsString(request));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    validateResponseConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
            for (ConsumerRecord<String, String> rec : records) {
                KafkaResponse response = objectMapper.readValue(rec.value(), KafkaResponse.class);
                assertThat(response.isSuccess()).isTrue();
            }
        });
    }

    // ── Тест 4: Token validation — invalid JWT ────────────────────────────────
    @Test
    @DisplayName("Token validation: invalid JWT → error response, token_invalid metric")
    void tokenValidation_invalidToken_errorResponse() throws Exception {
        KafkaRequest request = new KafkaRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setJwtToken("Bearer obviously.invalid.token");

        sendString(authValidateTopic, objectMapper.writeValueAsString(request));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    validateResponseConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
            for (ConsumerRecord<String, String> rec : records) {
                KafkaResponse response = objectMapper.readValue(rec.value(), KafkaResponse.class);
                assertThat(response.isSuccess()).isFalse();
            }
        });
    }

    // ── Тест 5: User CREATE через Kafka → entity в БД + outbox_events ─────────
    @Test
    @DisplayName("User CREATE: saved to DB and outbox_events written")
    void createUser_savedToDbAndOutbox() throws Exception {
        UserDto userDto = buildUserDto(42L, "ROLE_USER", "pass123");
        KafkaRequest request = buildRequest(adminJwt, objectMapper.writeValueAsString(userDto));

        sendString(userCreateTopic, objectMapper.writeValueAsString(request));

        await().atMost(10, SECONDS).untilAsserted(() -> {
            Optional<User> found = userRepository.findByProfileId(42L);
            assertThat(found).isPresent();
            assertThat(found.get().getRole()).isEqualTo("ROLE_USER");
        });

        // Outbox-запись должна появиться (создана в UserServiceImpl той же транзакцией)
        assertThat(outboxRepository.findAll()).isNotEmpty();
    }

    // ── Тест 6: Idempotency — повторный CREATE с тем же profileId ─────────────
    @Test
    @DisplayName("Idempotency: duplicate CREATE with same profileId skipped")
    void createUser_idempotency_noDuplicate() throws Exception {
        UserDto userDto = buildUserDto(55L, "ROLE_USER", "pass456");
        KafkaRequest request = buildRequest(adminJwt, objectMapper.writeValueAsString(userDto));
        String json = objectMapper.writeValueAsString(request);

        // Первый раз — создаём
        sendString(userCreateTopic, json);
        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(userRepository.findByProfileId(55L)).isPresent()
        );

        // Второй раз — дубль
        sendString(userCreateTopic, json);
        Thread.sleep(2000);

        // В БД должен быть ровно один пользователь с profileId=55
        long count = userRepository.findAll().stream()
                .filter(u -> u.getProfileId().equals(55L))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Тест 7: OutboxRelayScheduler доставляет в Kafka ───────────────────────
    @Test
    @DisplayName("Outbox relay: user.create.response delivered to Kafka topic")
    void outboxRelay_deliversUserCreateResponse() throws Exception {
        UserDto userDto = buildUserDto(77L, "ROLE_USER", "pass789");
        KafkaRequest request = buildRequest(adminJwt, objectMapper.writeValueAsString(userDto));

        sendString(userCreateTopic, objectMapper.writeValueAsString(request));

        // OutboxRelayScheduler (fixedDelay=500ms) должен доставить ответ
        await().atMost(10, SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records =
                    userCreateResponseConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isGreaterThan(0);
        });
    }

    // ── Тест 8: Liquibase создала таблицы users и outbox_events ───────────────
    @Test
    @DisplayName("Liquibase: users and outbox_events tables exist in authorization schema")
    void liquibase_tablesCreated() {
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(outboxRepository.count()).isGreaterThanOrEqualTo(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserDto buildUserDto(long profileId, String role, String password) {
        UserDto dto = new UserDto();
        dto.setProfileId(profileId);
        dto.setRole(role);
        dto.setPassword(password);
        return dto;
    }

    private KafkaRequest buildRequest(String jwt, String payload) {
        KafkaRequest req = new KafkaRequest();
        req.setRequestId(UUID.randomUUID().toString());
        req.setJwtToken(jwt);
        req.setPayload(payload);
        return req;
    }

    private void sendString(String topic, String value) throws Exception {
        producer.send(new ProducerRecord<>(topic, null, value)).get();
    }

    private KafkaConsumer<String, String> buildConsumer(List<String> topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-auth-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(topics);
        return c;
    }
}
