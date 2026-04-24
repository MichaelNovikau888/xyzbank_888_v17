package com.bank.profile.resource;

import com.bank.profile.entity.Profile;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты REST-эндпоинтов ProfileResource.
 /
 * Поднимается полный @QuarkusTest-контекст с H2 (профиль %test).
 * Kafka-каналы заменены на smallrye-in-memory (см. application.properties).
 */
@QuarkusTest
class ProfileResourceTest {

    @BeforeEach
    @Transactional
    void cleanDb() {
        Profile.deleteAll();
    }

    private Long createProfileInDb(String email, Long snils, Long inn) {
        Profile p = new Profile();
        p.setEmail(email);
        p.setSnils(snils);
        p.setInn(inn);
        p.setPhoneNumber("79001234567");
        p.setNameOnCard("Test User");
        persistProfile(p);
        return p.getId();
    }

    @Transactional
    void persistProfile(Profile p) {
        Profile.persist(p);
    }

    // ── GET /api/profiles ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profiles — 200 с пустым списком если БД пуста")
    void getAll_emptyDb_returns200() {
        given()
            .when().get("/api/profiles")
            .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @DisplayName("GET /api/profiles — возвращает сохранённые профили")
    void getAll_withData_returnsProfiles() {
        createProfileInDb("alice@example.com", 11100000001L, 111000000001L);
        createProfileInDb("bob@example.com",   22200000002L, 222000000002L);

        given()
            .when().get("/api/profiles")
            .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)));
    }

    // ── GET /api/profiles/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/profiles/{id} — возвращает профиль по ID")
    void getById_exists_returns200() {
        Long id = createProfileInDb("charlie@example.com", 33300000003L, 333000000003L);

        given()
            .when().get("/api/profiles/" + id)
            .then()
                .statusCode(200)
                .body("id",    equalTo(id.intValue()))
                .body("email", equalTo("charlie@example.com"));
    }

    @Test
    @DisplayName("GET /api/profiles/{id} — 404 если профиль не найден")
    void getById_notExists_returns404() {
        given()
            .when().get("/api/profiles/999999")
            .then()
                .statusCode(404);
    }

    // ── POST /api/profiles ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/profiles — 201 Created с телом ответа")
    void create_validDto_returns201() {
        String json = "{\"email\":\"new@example.com\",\"phoneNumber\":79009998877,\"nameOnCard\":\"New User\",\"snils\":44400000004,\"inn\":444000000004}";

        given()
            .contentType("application/json")
            .body(json)
            .when().post("/api/profiles")
            .then()
                .statusCode(201)
                .body("id",    notNullValue())
                .body("email", equalTo("new@example.com"));
    }

    @Test
    @DisplayName("POST /api/profiles — невалидный email — 400")
    void create_invalidEmail_returns400() {
        String json = "{\"email\":\"not-an-email\",\"snils\":55500000005,\"inn\":555000000005}";

        given()
            .contentType("application/json")
            .body(json)
            .when().post("/api/profiles")
            .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("POST /api/profiles — дублирующий email — 409")
    void create_duplicateEmail_returns409() {
        createProfileInDb("dup@example.com", 66600000006L, 666000000006L);

        String json = "{\"email\":\"dup@example.com\",\"snils\":77700000007,\"inn\":777000000007}";

        given()
            .contentType("application/json")
            .body(json)
            .when().post("/api/profiles")
            .then()
                .statusCode(409);
    }

    // ── PUT /api/profiles/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/profiles/{id} — 200 с обновлёнными данными")
    void update_exists_returns200() {
        Long id = createProfileInDb("old@example.com", 88800000008L, 888000000008L);

        String json = "{\"email\":\"updated@example.com\",\"phoneNumber\":79001111111,\"nameOnCard\":\"Updated\",\"snils\":88800000008,\"inn\":888000000008}";

        given()
            .contentType("application/json")
            .body(json)
            .when().put("/api/profiles/" + id)
            .then()
                .statusCode(200)
                .body("email", equalTo("updated@example.com"));
    }

    @Test
    @DisplayName("PUT /api/profiles/{id} — 404 если профиль не найден")
    void update_notExists_returns404() {
        String json = "{\"email\":\"nobody@example.com\",\"snils\":99900000009,\"inn\":999000000009}";

        given()
            .contentType("application/json")
            .body(json)
            .when().put("/api/profiles/999999")
            .then()
                .statusCode(404);
    }

    // ── DELETE /api/profiles/{id} ─────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/profiles/{id} — 204 No Content")
    void delete_exists_returns204() {
        Long id = createProfileInDb("todelete@example.com", 10100000010L, 101000000010L);

        given()
            .when().delete("/api/profiles/" + id)
            .then()
                .statusCode(204);

        // Проверяем — больше не найти
        given()
            .when().get("/api/profiles/" + id)
            .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("DELETE /api/profiles/{id} — 204 даже если не найден (идемпотентно)")
    void delete_notExists_returns204() {
        given()
            .when().delete("/api/profiles/999999")
            .then()
                .statusCode(204);
    }

    // ── /q/health ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /q/health — UP")
    void healthEndpoint_returnsUp() {
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
