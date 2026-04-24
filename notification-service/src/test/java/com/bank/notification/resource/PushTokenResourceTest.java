package com.bank.notification.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Интеграционные тесты:
 *   POST /api/v1/push/register  (PushTokenResource)
 *   GET  /q/health
 *   GET  /q/metrics
 */
 /**
 * Redis в тесте — мок через Quarkus DevServices или @InjectMock.
 * FCM — mock URL (quarkus.rest-client.fcm-api.url=http://localhost:9999).
 */
 /**
 * Покрытие:
 *   - 200 при корректном запросе
 *   - 400 при пустых clientId / pushToken / deviceType
 *   - 400 при полностью пустом body
 *   - health UP
 *   - metrics содержат notification счётчики
 */
@QuarkusTest
@DisplayName("PushTokenResource + Health + Metrics — integration tests")
class PushTokenResourceTest {

    private static final String REGISTER_URL = "/api/v1/push/register";

    // ── POST /api/v1/push/register ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/push/register")
    class RegisterPushToken {

        @Test
        @DisplayName("валидный запрос → 200 OK, message в ответе")
        void validRequest_returns200() {
            String body = """
                    {
                      "clientId":   "client-42",
                      "pushToken":  "fcm-token-xyz-789",
                      "deviceType": "android"
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(200)
                    .body("message",  notNullValue())
                    .body("clientId", equalTo("client-42"));
        }

        @Test
        @DisplayName("ios deviceType — 200 OK")
        void iosDevice_returns200() {
            String body = """
                    {
                      "clientId":   "client-ios",
                      "pushToken":  "apns-token-abc",
                      "deviceType": "ios"
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("пустой clientId → 400 Bad Request")
        void emptyClientId_returns400() {
            String body = """
                    {
                      "clientId":   "",
                      "pushToken":  "fcm-token",
                      "deviceType": "android"
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("пустой pushToken → 400 Bad Request")
        void emptyPushToken_returns400() {
            String body = """
                    {
                      "clientId":   "client-1",
                      "pushToken":  "",
                      "deviceType": "android"
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("пустой deviceType → 400 Bad Request")
        void emptyDeviceType_returns400() {
            String body = """
                    {
                      "clientId":   "client-1",
                      "pushToken":  "fcm-token",
                      "deviceType": ""
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(400)
                    .body("error", notNullValue());
        }

        @Test
        @DisplayName("пустое тело {} → 400 Bad Request")
        void emptyBody_returns400() {
            given()
                .contentType("application/json")
                .body("{}")
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("null clientId → 400 Bad Request")
        void nullClientId_returns400() {
            String body = """
                    {
                      "pushToken":  "fcm-token",
                      "deviceType": "android"
                    }
                    """;

            given()
                .contentType("application/json")
                .body(body)
                .when().post(REGISTER_URL)
                .then()
                    .statusCode(400);
        }
    }

    // ── GET /q/health ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /q/health")
    class Health {

        @Test
        @DisplayName("возвращает status=UP")
        void returns200Up() {
            given()
                .when().get("/q/health")
                .then()
                    .statusCode(200)
                    .body("status", equalTo("UP"));
        }
    }

    // ── GET /q/metrics ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /q/metrics")
    class Metrics {

        @Test
        @DisplayName("экспортирует notification_emails_sent_total")
        void exposesEmailsSentCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("notification_emails_sent_total"));
        }

        @Test
        @DisplayName("экспортирует notification_emails_failed_total")
        void exposesEmailsFailedCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("notification_emails_failed_total"));
        }

        @Test
        @DisplayName("экспортирует notification_idempotent_skipped_total")
        void exposesIdempotentSkippedCounter() {
            given()
                .when().get("/q/metrics")
                .then()
                    .statusCode(200)
                    .body(containsString("notification_idempotent_skipped_total"));
        }
    }
}
