package com.bank.authorization.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс интеграционных тестов authorization-service.
 /
 * Контейнеры static — запускаются один раз на весь тестовый прогон.
 * Init-скрипт создаёт схему authorization до того, как Liquibase
 * применяет changelog-001.xml и create-outbox-events.xml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AuthTestConfig.class)
public abstract class AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource") // static @Container — lifecycle managed by Testcontainers
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("db/init-authorization-schema.sql");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",          POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",     POSTGRES::getUsername);
        registry.add("spring.datasource.password",     POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // JWT — тот же ключ, что в application-local.yaml
        registry.add("app.jwt.secret-key",    () -> "nawEFeYYKPTxjDZOF+eoepmPza+CLhJd+g9m3GHcvro=");
        registry.add("app.jwt.expiration",    () -> "36000000");

        // Liquibase схема
        registry.add("spring.liquibase.default-schema", () -> "authorization");

        // Отключаем Kubernetes discovery
        registry.add("spring.cloud.kubernetes.enabled",           () -> "false");
        registry.add("spring.cloud.kubernetes.discovery.enabled", () -> "false");
        registry.add("spring.cloud.kubernetes.config.enabled",    () -> "false");
    }
}
