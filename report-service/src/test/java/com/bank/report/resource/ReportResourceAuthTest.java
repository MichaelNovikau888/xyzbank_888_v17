package com.bank.report.resource;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.*;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Интеграционные тесты JWT-авторизации в ReportResource.
 *
 * <p>Профиль {@link AuthEnabledProfile} включает {@code app.jwt.auth-enabled=true},
 * так что авторизационная логика активна. Тесты проверяют:
 * <ul>
 *   <li>401 при отсутствии токена</li>
 *   <li>403 при несовпадении clientId</li>
 *   <li>403 при обращении к /bank/ с токеном ROLE_USER</li>
 *   <li>200 при ROLE_ADMIN на любом clientId</li>
 *   <li>200 при корректном clientId в токене</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(ReportResourceAuthTest.AuthEnabledProfile.class)
@DisplayName("ReportResource — JWT authorization tests")
class ReportResourceAuthTest {

    // ── Test profile: включаем auth ───────────────────────────────────────────

    /**
     * Тестовый профиль с включённой JWT-авторизацией.
     * Переопределяет только app.jwt.auth-enabled=true; остальные свойства
     * наследуются из %test секции application.properties.
     */
    public static class AuthEnabledProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of("app.jwt.auth-enabled", "true");
        }
    }

    // ── Token factory ─────────────────────────────────────────────────────────

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2";

    private static Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    }

    private String userToken(Long clientId) {
        return Jwts.builder()
                .setSubject(String.valueOf(clientId))
                .claim("clientId", String.valueOf(clientId))
                .claim("authorities", List.of("ROLE_USER"))
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String adminToken() {
        return Jwts.builder()
                .setSubject("admin")
                .claim("authorities", List.of("ROLE_ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private String today() { return java.time.LocalDate.now().toString(); }

    // ═══════════════════════════════════════════════════════════════
    // /client/{clientId}/... — клиентские эндпоинты
    // ═══════════════════════════════════════════════════════════════

    @Nested @DisplayName("/client/{id}/payments/day — авторизация")
    class ClientPaymentAuth {

        @Test @DisplayName("без токена → 401")
        void noToken_401() {
            given().when()
                .get("/api/v1/reports/client/42/payments/day?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("пустой Authorization → 401")
        void emptyAuth_401() {
            given().header("Authorization", "")
                .when().get("/api/v1/reports/client/42/payments/day?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("токен без Bearer-префикса → 401")
        void noBearer_401() {
            given().header("Authorization", userToken(42L))
                .when().get("/api/v1/reports/client/42/payments/day?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("ROLE_USER, свой clientId → 200")
        void ownClientId_200() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/client/42/payments/day?date=" + today())
                .then().statusCode(200);
        }

        @Test @DisplayName("ROLE_USER, чужой clientId → 403")
        void foreignClientId_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/client/99/payments/day?date=" + today())
                .then().statusCode(403)
                .body(containsString("clientId mismatch"));
        }

        @Test @DisplayName("ROLE_ADMIN, любой clientId → 200")
        void adminAnyClientId_200() {
            given().header("Authorization", bearer(adminToken()))
                .when().get("/api/v1/reports/client/99/payments/day?date=" + today())
                .then().statusCode(200);
        }
    }

    @Nested @DisplayName("/client/{id}/summary/day — авторизация")
    class ClientSummaryAuth {

        @Test @DisplayName("без токена → 401")
        void noToken_401() {
            given().when()
                .get("/api/v1/reports/client/42/summary/day?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("ROLE_USER, свой clientId → 200")
        void ownClientId_200() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/client/42/summary/day?date=" + today())
                .then().statusCode(200);
        }

        @Test @DisplayName("ROLE_USER, чужой clientId → 403")
        void foreignClientId_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/client/1/summary/day?date=" + today())
                .then().statusCode(403);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // /bank/... — бухгалтерские эндпоинты (только ROLE_ADMIN)
    // ═══════════════════════════════════════════════════════════════

    @Nested @DisplayName("/bank/payments/day — авторизация")
    class BankPaymentAuth {

        @Test @DisplayName("без токена → 401")
        void noToken_401() {
            given().when()
                .get("/api/v1/reports/bank/payments/day?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("ROLE_USER → 403")
        void userRole_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/bank/payments/day?date=" + today())
                .then().statusCode(403)
                .body(containsString("ROLE_ADMIN required"));
        }

        @Test @DisplayName("ROLE_ADMIN → 200")
        void adminRole_200() {
            given().header("Authorization", bearer(adminToken()))
                .when().get("/api/v1/reports/bank/payments/day?date=" + today())
                .then().statusCode(200);
        }
    }

    @Nested @DisplayName("/bank/summary/day — авторизация")
    class BankSummaryAuth {

        @Test @DisplayName("ROLE_USER → 403")
        void userRole_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/bank/summary/day?date=" + today())
                .then().statusCode(403);
        }

        @Test @DisplayName("ROLE_ADMIN → 200")
        void adminRole_200() {
            given().header("Authorization", bearer(adminToken()))
                .when().get("/api/v1/reports/bank/summary/day?date=" + today())
                .then().statusCode(200);
        }
    }

    @Nested @DisplayName("/bank/daily (CSV) — авторизация")
    class BankCsvAuth {

        @Test @DisplayName("без токена → 401")
        void noToken_401() {
            given().when()
                .get("/api/v1/reports/bank/daily?date=" + today())
                .then().statusCode(401);
        }

        @Test @DisplayName("ROLE_USER → 403")
        void userRole_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/bank/daily?date=" + today())
                .then().statusCode(403);
        }

        @Test @DisplayName("ROLE_ADMIN → 200 CSV")
        void adminRole_200() {
            given().header("Authorization", bearer(adminToken()))
                .when().get("/api/v1/reports/bank/daily?date=" + today())
                .then().statusCode(200)
                .contentType(containsString("text/csv"));
        }
    }

    @Nested @DisplayName("/bank/partitions/health — авторизация")
    class PartitionHealthAuth {

        @Test @DisplayName("ROLE_USER → 403")
        void userRole_403() {
            given().header("Authorization", bearer(userToken(42L)))
                .when().get("/api/v1/reports/bank/partitions/health")
                .then().statusCode(403);
        }

        @Test @DisplayName("ROLE_ADMIN → 200")
        void adminRole_200() {
            given().header("Authorization", bearer(adminToken()))
                .when().get("/api/v1/reports/bank/partitions/health")
                .then().statusCode(200);
        }
    }
}
