package com.bank.antifraud.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс интеграционных тестов antifraud-service.
 * <p>
 * Особенности antifraud:
 * - Spring Data JDBC (не JPA) — нет Hibernate, нет Session
 * - Liquibase управляет схемой: suspicious_card_transfer, suspicious_account_transfers,
 * suspicious_phone_transfers, audit, outbox_events
 * - @DynamicPropertySource переопределяет datasource и kafka на контейнерные порты
 * <p>
 * Контейнеры static — запускаются один раз на весь тестовый прогон.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    // static @Container — lifecycle managed by Testcontainers, try-with-resources not applicable
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.liquibase.default-schema", () -> "public");
        registry.add("spring.kafka.group-id", () -> "test-antifraud-group");
    }
}
