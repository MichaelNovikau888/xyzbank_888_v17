package com.bank.publicinfo.resource;

import com.bank.publicinfo.entity.BankDetails;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты BankDetailsResource.
 * Поднимается полный @QuarkusTest-контекст с H2 (профиль %test).
 * Kafka-каналы → smallrye-in-memory.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BankDetailsResourceTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    @BeforeEach
    @Transactional
    void cleanDb() {
        BankDetails.deleteAll();
    }

    private void persistBank(long bik, long inn, String kpp, String cor, String city, String jsc, String name) {
        BankDetails b = new BankDetails();
        b.setBik(bik);
        b.setInn(inn);
        b.setKpp(kpp);
        b.setCorAccount(cor);
        b.setCity(city);
        b.setJointStockCompany(jsc);
        b.setName(name);
        b.persistAndFlush();
    }

    private String bankJson(long bik, long inn, String kpp, String cor,
                            String city, String jsc, String name) {
        return String.format(
                "{\"bik\":%d,\"inn\":%d,\"kpp\":%s,\"corAccount\":%s," +
                        "\"city\":\"%s\",\"jointStockCompany\":\"%s\",\"name\":\"%s\"}",
                bik, inn, kpp, cor, city, jsc, name);
    }

    // ── GET /api/public-info/bank-details ─────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /bank-details → 200 пустой список")
    void getAll_empty_returns200() {
        given().when().get("/api/public-info/bank-details")
                .then().statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content", hasSize(0));
    }

    @Test
    @Order(2)
    @Transactional
    @DisplayName("GET /bank-details → возвращает все записи с пагинацией")
    void getAll_withData_returnsPaged() {
        persistBank(44525225L, 7710140679L, "771001001", "301018104000000225", "Москва", "ПАО", "Банк А");
        persistBank(44525226L, 7710140680L, "771001002", "301018104000000226", "СПб", "ОАО", "Банк Б");

        given().when().get("/api/public-info/bank-details")
                .then().statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content", hasSize(2));
    }

    @Test
    @Order(3)
    @DisplayName("GET /bank-details?page=0&size=1 → пагинация")
    @Transactional
    void getAll_pagination_works() {
        persistBank(44525225L, 7710140679L, "771001001", "301018104000000225", "Москва", "ПАО", "Банк А");
        persistBank(44525226L, 7710140680L, "771001002", "301018104000000226", "СПб", "ОАО", "Банк Б");

        given().queryParam("page", 0).queryParam("size", 1)
                .when().get("/api/public-info/bank-details")
                .then().statusCode(200)
                .body("totalElements", equalTo(2))
                .body("totalPages", equalTo(2))
                .body("content", hasSize(1));
    }

    // ── GET /api/public-info/bank-details/{id} ────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /bank-details/9999 → 404")
    void getById_notFound_returns404() {
        given().when().get("/api/public-info/bank-details/9999")
                .then().statusCode(404)
                .body("errorCode", equalTo("ENTITY_NOT_FOUND"));
    }

    @Test
    @Order(5)
    @DisplayName("GET /bank-details/{id} → 200 с правильными данными")
    @Transactional
    void getById_found_returns200() {
        BankDetails b = new BankDetails();
        b.setBik(44525999L);
        b.setInn(7710140999L);
        b.setKpp("771001999");
        b.setCorAccount("301018104000000999");
        b.setCity("Казань");
        b.setJointStockCompany("ПАО");
        b.setName("Тест-Банк");
        b.persistAndFlush();

        given().when().get("/api/public-info/bank-details/" + b.getId())
                .then().statusCode(200)
                .body("name", equalTo("Тест-Банк"))
                .body("city", equalTo("Казань"));
    }

    // ── POST /api/public-info/bank-details ────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("POST /bank-details → 201 с созданной сущностью")
    void create_valid_returns201() {
        String body = bankJson(44525225L, 7710140679L, "771001001",
                "301018104000000225", "Москва", "ПАО", "Сбербанк");
        given().contentType("application/json").body(body)
                .when().post("/api/public-info/bank-details")
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Сбербанк"))
                .body("city", equalTo("Москва"));
    }

    @Test
    @Order(7)
    @DisplayName("POST /bank-details без обязательных полей → 400")
    void create_missingFields_returns400() {
        given().contentType("application/json").body("{\"city\":\"Москва\"}")
                .when().post("/api/public-info/bank-details")
                .then().statusCode(400);
    }

    // ── PUT /api/public-info/bank-details/{id} ────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("PUT /bank-details/{id} → 200 обновлённая сущность")
    @Transactional
    void update_valid_returns200() {
        BankDetails b = new BankDetails();
        b.setBik(44525300L);
        b.setInn(7710140300L);
        b.setKpp("771001300");
        b.setCorAccount("301018104000000300");
        b.setCity("Москва");
        b.setJointStockCompany("ПАО");
        b.setName("Старое имя");
        b.persistAndFlush();

        String update = bankJson(44525300L, 7710140300L, "771001300",
                "301018104000000300", "СПб", "ОАО", "Новое имя");
        given().contentType("application/json").body(update)
                .when().put("/api/public-info/bank-details/" + b.getId())
                .then().statusCode(200)
                .body("name", equalTo("Новое имя"))
                .body("city", equalTo("СПб"));
    }

    @Test
    @Order(9)
    @DisplayName("PUT /bank-details/9999 → 404")
    void update_notFound_returns404() {
        String body = bankJson(44525225L, 7710140679L, "771001001",
                "301018104000000225", "Москва", "ПАО", "X");
        given().contentType("application/json").body(body)
                .when().put("/api/public-info/bank-details/9999")
                .then().statusCode(404);
    }

    // ── DELETE /api/public-info/bank-details/{id} ─────────────────────────────

    @Test
    @Order(10)
    @DisplayName("DELETE /bank-details/{id} → 204")
    @Transactional
    void delete_exists_returns204() {
        BankDetails b = new BankDetails();
        b.setBik(44525400L);
        b.setInn(7710140400L);
        b.setKpp("771001400");
        b.setCorAccount("301018104000000400");
        b.setCity("Москва");
        b.setJointStockCompany("ПАО");
        b.setName("Удалить");
        b.persistAndFlush();

        given().when().delete("/api/public-info/bank-details/" + b.getId())
                .then().statusCode(204);
    }

    @Test
    @Order(11)
    @DisplayName("DELETE /bank-details/9999 → 404")
    void delete_notFound_returns404() {
        given().when().delete("/api/public-info/bank-details/9999")
                .then().statusCode(404);
    }

    // ── Health / Metrics ──────────────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("GET /q/health → UP")
    void health_returnsUp() {
        given().when().get("/q/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @Order(13)
    @DisplayName("GET /q/metrics → содержит publicinfo метрики")
    void metrics_exposesCounters() {
        given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("publicinfo_bank_details_created_total"))
                .body(containsString("publicinfo_kafka_errors_total"));
    }
}
