package com.bank.profile.integration;

import com.bank.profile.entity.Profile;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты ProfileResource (profile-service).
 */
 /**
 * Поднимается полный @QuarkusTest-контекст:
 *   - H2 in-memory (тестовый профиль: drop-and-create)
 *   - Kafka → smallrye-in-memory (конфиг в application.properties)
 *   - Liquibase отключён (%test)
 */
 /**
 * Тесты покрывают полный HTTP-цикл:
 *   GET  /api/profiles          — список всех профилей
 *   GET  /api/profiles/{id}     — профиль по ID
 *   POST /api/profiles          — создание профиля
 *   PUT  /api/profiles/{id}     — обновление профиля
 *   DELETE /api/profiles/{id}   — удаление профиля
 */
 /**
 * Проверяется:
 *   - Статусы ответов (200, 201, 204, 400, 404, 409)
 *   - Тела ответов и конкретные поля
 *   - Валидация (@Email на email)
 *   - Уникальность (дублирующий email → 409)
 *   - Здоровье сервиса /q/health
 */
@QuarkusTest
@DisplayName("ProfileResource — integration tests")
class ProfileIntegrationTest {

    @BeforeEach
    @Transactional
    void cleanDb() {
        Profile.deleteAll();
    }

    @Transactional
    Long createProfile(String email, Long snils, Long inn) {
        Profile p = new Profile();
        p.setEmail(email);
        p.setSnils(snils);
        p.setInn(inn);
        p.setPhoneNumber("79001234567");
        p.setNameOnCard("Test User");
        Profile.persist(p);
        return p.getId();
    }

    // ── GET /api/profiles ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/profiles")
    class GetAll {

        @Test
        @DisplayName("200 — пустой список если БД пуста")
        void emptyDb_returns200EmptyList() {
            given()
                .when().get("/api/profiles")
                .then()
                    .statusCode(200)
                    .body("$", hasSize(0));
        }

        @Test
        @DisplayName("200 — возвращает все профили")
        void withData_returnsAllProfiles() {
            createProfile("alice@test.com", 11100000001L, 111000000001L);
            createProfile("bob@test.com",   22200000002L, 222000000002L);

            given()
                .when().get("/api/profiles")
                .then()
                    .statusCode(200)
                    .body("$", hasSize(greaterThanOrEqualTo(2)));
        }
    }

    // ── GET /api/profiles/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/profiles/{id}")
    class GetById {

        @Test
        @DisplayName("200 — профиль найден, поля корректны")
        void found_returns200WithFields() {
            Long id = createProfile("charlie@test.com", 33300000003L, 333000000003L);

            given()
                .when().get("/api/profiles/" + id)
                .then()
                    .statusCode(200)
                    .body("id",    equalTo(id.intValue()))
                    .body("email", equalTo("charlie@test.com"));
        }

        @Test
        @DisplayName("404 — профиль не найден")
        void notFound_returns404() {
            given()
                .when().get("/api/profiles/999999")
                .then()
                    .statusCode(404);
        }
    }

    // ── POST /api/profiles ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/profiles")
    class Create {

        @Test
        @DisplayName("201 — профиль создан, id и email в ответе")
        void validData_returns201() {
            String json = """
                    {"email":"new@test.com","phoneNumber":"79009998877",
                     "nameOnCard":"New User","snils":44400000004,"inn":444000000004}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/profiles")
                .then()
                    .statusCode(201)
                    .body("id",    notNullValue())
                    .body("email", equalTo("new@test.com"));
        }

        @Test
        @DisplayName("400 — невалидный email")
        void invalidEmail_returns400() {
            String json = """
                    {"email":"not-an-email","snils":55500000005,"inn":555000000005}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/profiles")
                .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("409 — дублирующий email")
        void duplicateEmail_returns409() {
            createProfile("dup@test.com", 66600000006L, 666000000006L);

            String json = """
                    {"email":"dup@test.com","snils":77700000007,"inn":777000000007}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/profiles")
                .then()
                    .statusCode(409);
        }

        @Test
        @DisplayName("409 — дублирующий СНИЛС")
        void duplicateSnils_returns409() {
            createProfile("original@test.com", 88800000008L, 888000000008L);

            String json = """
                    {"email":"unique@test.com","snils":88800000008,"inn":999000000009}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().post("/api/profiles")
                .then()
                    .statusCode(409);
        }
    }

    // ── PUT /api/profiles/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/profiles/{id}")
    class Update {

        @Test
        @DisplayName("200 — профиль обновлён")
        void exists_returns200Updated() {
            Long id = createProfile("old@test.com", 10100000010L, 101000000010L);

            String json = """
                    {"email":"updated@test.com","phoneNumber":"79001111111",
                     "nameOnCard":"Updated","snils":10100000010,"inn":101000000010}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().put("/api/profiles/" + id)
                .then()
                    .statusCode(200)
                    .body("email", equalTo("updated@test.com"));
        }

        @Test
        @DisplayName("404 — профиль не найден")
        void notFound_returns404() {
            String json = """
                    {"email":"nobody@test.com","snils":99999999999,"inn":999999999999}
                    """;

            given()
                .contentType("application/json")
                .body(json)
                .when().put("/api/profiles/999999")
                .then()
                    .statusCode(404);
        }
    }

    // ── DELETE /api/profiles/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/profiles/{id}")
    class Delete {

        @Test
        @DisplayName("204 — профиль удалён")
        void exists_returns204() {
            Long id = createProfile("todelete@test.com", 20200000020L, 202000000020L);

            given()
                .when().delete("/api/profiles/" + id)
                .then()
                    .statusCode(204);

            given()
                .when().get("/api/profiles/" + id)
                .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("204 — идемпотентно (не найден → тоже 204)")
        void notExists_returns204Idempotent() {
            given()
                .when().delete("/api/profiles/999999")
                .then()
                    .statusCode(204);
        }
    }

    // ── /q/health ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Health & Metrics")
    class HealthAndMetrics {

        @Test
        @DisplayName("GET /q/health → 200 UP")
        void health_returnsUp() {
            given()
                .when().get("/q/health")
                .then()
                    .statusCode(200)
                    .body("status", equalTo("UP"));
        }

        @Test
        @DisplayName("GET /q/metrics → содержит profile_created_total")
        void metrics_exposesProfileCounters() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("profile_created_total"));
        }
    }
}
