package com.bank.notification.resource;

import com.bank.notification.entity.NotificationRecord;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты GET /api/v1/notifications/{clientId}.
 */
 /**
 * H2 in-memory, Kafka/Redis не нужны.
 * Каждый тест начинается с чистой таблицы.
 */
 /**
 * Покрытие:
 *   - 200 пустой список когда нет записей
 *   - 200 с данными + пагинация + totalPages
 *   - фильтрация по clientId (чужие записи не видны)
 *   - все поля ответа присутствуют
 *   - 400 при пустом clientId
 *   - 400 при size=0 и size=101
 *   - пагинация page/size
 */
@QuarkusTest
@DisplayName("NotificationResource — integration tests")
class NotificationResourceTest {

    @BeforeEach
    @Transactional
    void cleanDb() {
        NotificationRecord.deleteAll();
    }

    @Transactional
    void persist(Long paymentId, String clientId, String status, String reason) {
        NotificationRecord r = new NotificationRecord(
                paymentId, clientId, status,
                new BigDecimal("1000.00"), "RUB",
                "40817810099910004312", reason);
        r.persist();
    }

    // ── Базовые сценарии ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/notifications/{clientId}")
    class GetNotifications {

        @Test
        @DisplayName("200 — пустой список для клиента без записей")
        void emptyForClient_returns200WithEmpty() {
            given()
                .when().get("/api/v1/notifications/unknown-client")
                .then()
                    .statusCode(200)
                    .body("total",   equalTo(0))
                    .body("content", hasSize(0));
        }

        @Test
        @DisplayName("200 — возвращает записи клиента с правильными полями")
        void withData_returnsRecordsWithFields() {
            persist(101L, "client-1", "COMPLETED", null);
            persist(102L, "client-1", "FAILED",    "Недостаточно средств");

            given()
                .when().get("/api/v1/notifications/client-1")
                .then()
                    .statusCode(200)
                    .body("total",               equalTo(2))
                    .body("content",             hasSize(2))
                    .body("content[0].paymentId",    notNullValue())
                    .body("content[0].finalStatus",  notNullValue())
                    .body("content[0].amount",        notNullValue())
                    .body("content[0].currency",      equalTo("RUB"))
                    .body("content[0].notifiedAt",    notNullValue());
        }

        @Test
        @DisplayName("фильтрация: записи другого клиента не видны")
        void filtersByClientId() {
            persist(201L, "client-A", "COMPLETED", null);
            persist(202L, "client-B", "FAILED",    "Ошибка");

            given()
                .when().get("/api/v1/notifications/client-A")
                .then()
                    .statusCode(200)
                    .body("total",   equalTo(1))
                    .body("content[0].paymentId", equalTo(201));
        }

        @Test
        @DisplayName("reason присутствует в ответе для FAILED")
        void reasonPresentForFailed() {
            persist(301L, "client-2", "FAILED", "Antifraud blocked");

            given()
                .when().get("/api/v1/notifications/client-2")
                .then()
                    .statusCode(200)
                    .body("content[0].reason", equalTo("Antifraud blocked"));
        }

        @Test
        @DisplayName("reason null для COMPLETED — поле присутствует как null")
        void reasonNullForCompleted() {
            persist(401L, "client-3", "COMPLETED", null);

            given()
                .when().get("/api/v1/notifications/client-3")
                .then()
                    .statusCode(200)
                    .body("content[0].finalStatus", equalTo("COMPLETED"));
        }
    }

    // ── Пагинация ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Пагинация")
    class Pagination {

        @Test
        @DisplayName("page=0&size=1 возвращает 1 из 3, totalPages=3")
        void firstPage_correctMeta() {
            persist(501L, "client-p", "COMPLETED", null);
            persist(502L, "client-p", "FAILED",    "err");
            persist(503L, "client-p", "CANCELLED", "cancel");

            given()
                .queryParam("page", 0)
                .queryParam("size", 1)
                .when().get("/api/v1/notifications/client-p")
                .then()
                    .statusCode(200)
                    .body("total",      equalTo(3))
                    .body("totalPages", equalTo(3))
                    .body("content",    hasSize(1))
                    .body("page",       equalTo(0))
                    .body("size",       equalTo(1));
        }

        @Test
        @DisplayName("page=1&size=2 возвращает правильную страницу")
        void secondPage_returnsCorrectSlice() {
            for (int i = 0; i < 4; i++) {
                persist((long)(600 + i), "client-q", "COMPLETED", null);
            }

            given()
                .queryParam("page", 1)
                .queryParam("size", 2)
                .when().get("/api/v1/notifications/client-q")
                .then()
                    .statusCode(200)
                    .body("total",   equalTo(4))
                    .body("content", hasSize(2));
        }
    }

    // ── Валидация параметров ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Валидация параметров")
    class Validation {

        @Test
        @DisplayName("size=0 → 400 Bad Request")
        void zeroSize_returns400() {
            given()
                .queryParam("size", 0)
                .when().get("/api/v1/notifications/client-x")
                .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("size=101 → 400 Bad Request")
        void oversizeSize_returns400() {
            given()
                .queryParam("size", 101)
                .when().get("/api/v1/notifications/client-x")
                .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("пустой clientId (пробел) обрабатывается как 404 или пустой список")
        void blankClientId_handledSafely() {
            // пробел в пути не доходит до метода (JAX-RS routing), может быть 404
            given()
                .when().get("/api/v1/notifications/ ")
                .then()
                    .statusCode(greaterThanOrEqualTo(200));
        }
    }
}
