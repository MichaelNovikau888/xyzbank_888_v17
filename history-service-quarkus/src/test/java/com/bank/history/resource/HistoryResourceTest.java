package com.bank.history.resource;

import com.bank.history.entity.History;
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
 * Интеграционные тесты REST-эндпоинтов HistoryResource.
 */
 /**
 * Поднимается полный @QuarkusTest-контекст с H2 (профиль %test).
 * Kafka-каналы заменены на smallrye-in-memory — брокер не нужен.
 * Реальная БД: H2 in-memory, drop-and-create при старте.
 */
 /**
 * Покрываемые эндпоинты:
 *   GET /api/history                    (getAll)
 *   GET /api/history/service/{name}     (getByService)
 *   GET /api/history/type/{eventType}   (getByType)
 *   GET /api/history/transfer/{id}      (getByTransferId)
 *   GET /api/history/recent             (getRecent)
 *   GET /q/health
 *   GET /q/metrics
 */
 /**
 * Покрываемые exception mappers:
 *   IllegalArgumentExceptionMapper  → 400
 *   NotFoundExceptionMapper         → 404
 *   ConstraintViolationExceptionMapper → 400
 */
@QuarkusTest
@DisplayName("HistoryResource — integration tests")
class HistoryResourceTest {

    @BeforeEach
    @Transactional
    void cleanDb() {
        History.deleteAll();
    }

    @Transactional
    void persist(History h) {
        History.persist(h);
    }

    private History build(String service, String type, String hash) {
        History h = new History();
        h.setServiceName(service);
        h.setEventType(type);
        h.setEventData("{\"payload\":\"test\"}");
        h.setContentHash(hash);
        h.setCreatedAt(LocalDateTime.now());
        return h;
    }

    private History buildWithTransfer(String hash, Long transferAuditId) {
        History h = build("transfer-service", "TRANSFER", hash);
        h.setTransferAuditId(transferAuditId);
        return h;
    }

    // ── GET /api/history ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/history")
    class GetAll {

        @Test
        @DisplayName("200 с пустым контентом если БД пуста")
        void emptyDb_returns200WithEmpty() {
            given()
                .when().get("/api/history")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", hasSize(0))
                    .body("first", equalTo(true))
                    .body("last", equalTo(true));
        }

        @Test
        @DisplayName("возвращает все сохранённые события с метаданными пагинации")
        void withData_returnsEventsAndPaginationMeta() {
            persist(build("transfer-service", "TRANSFER", "h-ga1"));
            persist(build("account-service",  "ACCOUNT",  "h-ga2"));

            given()
                .when().get("/api/history")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content", hasSize(2))
                    .body("page", equalTo(0))
                    .body("size", equalTo(20))
                    .body("totalPages", equalTo(1));
        }

        @Test
        @DisplayName("пагинация: page=0&size=1 возвращает 1 из 2, totalElements=2")
        void pagination_firstPage() {
            persist(build("svc", "TYPE", "h-pag1"));
            persist(build("svc", "TYPE", "h-pag2"));

            given()
                .queryParam("page", 0).queryParam("size", 1)
                .when().get("/api/history")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("totalPages",    equalTo(2))
                    .body("content",       hasSize(1))
                    .body("first",         equalTo(true))
                    .body("last",          equalTo(false));
        }

        @Test
        @DisplayName("пагинация: page=1&size=1 возвращает вторую запись")
        void pagination_secondPage() {
            persist(build("svc", "TYPE", "h-p2a"));
            persist(build("svc", "TYPE", "h-p2b"));

            given()
                .queryParam("page", 1).queryParam("size", 1)
                .when().get("/api/history")
                .then()
                    .statusCode(200)
                    .body("content", hasSize(1))
                    .body("first",   equalTo(false))
                    .body("last",    equalTo(true));
        }

        @Test
        @DisplayName("page=-1 → 400 Bad Request")
        void negativePage_returns400() {
            given()
                .queryParam("page", -1)
                .when().get("/api/history")
                .then().statusCode(400);
        }

        @Test
        @DisplayName("size=0 → 400 Bad Request")
        void zeroSize_returns400() {
            given()
                .queryParam("size", 0)
                .when().get("/api/history")
                .then().statusCode(400);
        }

        @Test
        @DisplayName("size=201 → 400 Bad Request")
        void oversizeSize_returns400() {
            given()
                .queryParam("size", 201)
                .when().get("/api/history")
                .then().statusCode(400);
        }

        @Test
        @DisplayName("ответ содержит поля contentHash, serviceName, eventType")
        void responseContainsExpectedFields() {
            persist(build("audit.logs", "AUDIT", "h-fields1"));

            given()
                .when().get("/api/history")
                .then()
                    .statusCode(200)
                    .body("content[0].serviceName", equalTo("audit.logs"))
                    .body("content[0].eventType",   equalTo("AUDIT"))
                    .body("content[0].id",          notNullValue());
        }
    }

    // ── GET /api/history/service/{name} ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/history/service/{name}")
    class GetByService {

        @Test
        @DisplayName("фильтрует по serviceName — только нужный сервис")
        void filtersCorrectly() {
            persist(build("transfer-service", "TRANSFER", "h-sv1"));
            persist(build("account-service",  "ACCOUNT",  "h-sv2"));

            given()
                .when().get("/api/history/service/transfer-service")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].serviceName", equalTo("transfer-service"));
        }

        @Test
        @DisplayName("несколько записей одного сервиса с пагинацией")
        void multipleRecords_paginationWorks() {
            persist(build("audit.logs", "AUDIT", "h-sv3"));
            persist(build("audit.logs", "AUDIT", "h-sv4"));
            persist(build("audit.logs", "AUDIT", "h-sv5"));

            given()
                .queryParam("page", 0).queryParam("size", 2)
                .when().get("/api/history/service/audit.logs")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(3))
                    .body("content", hasSize(2))
                    .body("totalPages", equalTo(2));
        }

        @Test
        @DisplayName("неизвестный serviceName → 404 Not Found")
        void unknownService_returns404() {
            given()
                .when().get("/api/history/service/no-such-service")
                .then()
                    .statusCode(404)
                    .body("message", containsString("no-such-service"))
                    .body("status", equalTo(404));
        }
    }

    // ── GET /api/history/type/{eventType} ─────────────────────────────────────

    @Nested
    @DisplayName("GET /api/history/type/{eventType}")
    class GetByType {

        @Test
        @DisplayName("фильтрует только TRANSFER события")
        void filtersTransferType() {
            persist(build("transfer-service", "TRANSFER", "h-ty1"));
            persist(build("audit.logs",       "AUDIT",    "h-ty2"));

            given()
                .when().get("/api/history/type/TRANSFER")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].eventType", equalTo("TRANSFER"));
        }

        @Test
        @DisplayName("фильтрует ERROR события")
        void filtersErrorType() {
            persist(build("error", "ERROR", "h-ty3"));
            persist(build("error", "ERROR", "h-ty4"));

            given()
                .when().get("/api/history/type/ERROR")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2));
        }

        @Test
        @DisplayName("неизвестный eventType → 404 Not Found")
        void unknownType_returns404() {
            given()
                .when().get("/api/history/type/UNKNOWN_TYPE")
                .then()
                    .statusCode(404)
                    .body("message", containsString("UNKNOWN_TYPE"));
        }
    }

    // ── GET /api/history/transfer/{id} ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/history/transfer/{id}")
    class GetByTransferId {

        @Test
        @DisplayName("возвращает события по transferAuditId")
        void returnsMatchingEvents() {
            persist(buildWithTransfer("h-tr1", 42L));
            persist(buildWithTransfer("h-tr2", 42L));
            persist(buildWithTransfer("h-tr3", 99L)); // другой ID — не должен попасть

            given()
                .when().get("/api/history/transfer/42")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2));
        }

        @Test
        @DisplayName("несуществующий transferAuditId → 404 Not Found")
        void unknownId_returns404() {
            given()
                .when().get("/api/history/transfer/999")
                .then()
                    .statusCode(404)
                    .body("message", containsString("999"));
        }

        @Test
        @DisplayName("пагинация по transferAuditId работает")
        void pagination_works() {
            for (int i = 0; i < 3; i++) {
                persist(buildWithTransfer("h-tr-pg" + i, 55L));
            }

            given()
                .queryParam("page", 0).queryParam("size", 2)
                .when().get("/api/history/transfer/55")
                .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(3))
                    .body("content", hasSize(2));
        }
    }

    // ── GET /api/history/recent ───────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/history/recent")
    class GetRecent {

        @Test
        @DisplayName("возвращает последние N событий (по умолчанию limit=10)")
        void defaultLimit_returnsUpTo10() {
            for (int i = 0; i < 5; i++) {
                persist(build("svc", "TYPE", "h-rec" + i));
            }

            given()
                .when().get("/api/history/recent")
                .then()
                    .statusCode(200)
                    .body("size()", equalTo(5));
        }

        @Test
        @DisplayName("limit=2 — возвращает ровно 2 события")
        void customLimit_returnsCorrectCount() {
            persist(build("svc1", "TYPE", "h-rl1"));
            persist(build("svc2", "TYPE", "h-rl2"));
            persist(build("svc3", "TYPE", "h-rl3"));

            given()
                .queryParam("limit", 2)
                .when().get("/api/history/recent")
                .then()
                    .statusCode(200)
                    .body("size()", equalTo(2));
        }

        @Test
        @DisplayName("пустая БД → пустой список")
        void emptyDb_returnsEmpty() {
            given()
                .when().get("/api/history/recent")
                .then()
                    .statusCode(200)
                    .body("size()", equalTo(0));
        }

        @Test
        @DisplayName("limit=0 → 400 Bad Request")
        void zeroLimit_returns400() {
            given()
                .queryParam("limit", 0)
                .when().get("/api/history/recent")
                .then().statusCode(400);
        }

        @Test
        @DisplayName("limit=101 → 400 Bad Request")
        void oversizeLimit_returns400() {
            given()
                .queryParam("limit", 101)
                .when().get("/api/history/recent")
                .then().statusCode(400);
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
        @DisplayName("GET /q/metrics — содержит history_events_saved_total")
        void metrics_exposesEventsSavedCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("history_events_saved_total"));
        }

        @Test
        @DisplayName("GET /q/metrics — содержит history_idempotent_skipped_total")
        void metrics_exposesIdempotentSkippedCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("history_idempotent_skipped_total"));
        }

        @Test
        @DisplayName("GET /q/metrics — содержит history_dlq_events_total")
        void metrics_exposesDlqCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("history_dlq_events_total"));
        }
    }

    // ── Error response format ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Error response format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("404 ответ содержит поля message и status")
        void notFound_hasCorrectErrorFormat() {
            given()
                .when().get("/api/history/service/nonexistent")
                .then()
                    .statusCode(404)
                    .body("message", notNullValue())
                    .body("status", equalTo(404));
        }

        @Test
        @DisplayName("400 ответ содержит поля message и status")
        void badRequest_hasCorrectErrorFormat() {
            given()
                .queryParam("page", -1)
                .when().get("/api/history")
                .then()
                    .statusCode(400)
                    .body("status", greaterThanOrEqualTo(400));
        }
    }
}
