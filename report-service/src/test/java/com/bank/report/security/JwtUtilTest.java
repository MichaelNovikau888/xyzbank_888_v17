package com.bank.report.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit-тесты JwtUtil.
 *
 * <p>Токены генерируются тем же ключом, что задан через
 * {@code %test.app.jwt.secret-key} в application.properties.
 * Структура токена совпадает с authorization-service:
 * sub=userId, claim clientId (опц.), claim authorities=[ROLE_USER|ROLE_ADMIN].
 */
@QuarkusTest
@DisplayName("JwtUtil — unit tests")
class JwtUtilTest {

    @Inject
    JwtUtil jwtUtil;

    // Тестовый ключ — совпадает с %test.app.jwt.secret-key в application.properties
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2";

    // ── token helpers ─────────────────────────────────────────────────────────

    private String buildToken(String subject, String clientId, List<String> roles) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        var builder = Jwts.builder()
                .setSubject(subject)
                .claim("authorities", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, SignatureAlgorithm.HS256);
        if (clientId != null) builder.claim("clientId", clientId);
        return builder.compact();
    }

    private String userToken(Long clientId) {
        return buildToken(String.valueOf(clientId), String.valueOf(clientId),
                List.of("ROLE_USER"));
    }

    private String adminToken() {
        return buildToken("admin", null, List.of("ROLE_ADMIN"));
    }

    private String expiredToken() {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .setSubject("42")
                .claim("authorities", List.of("ROLE_USER"))
                .setExpiration(new Date(System.currentTimeMillis() - 1000L))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String bearer(String token) { return "Bearer " + token; }

    // ── extractClientId ───────────────────────────────────────────────────────

    @Nested @DisplayName("extractClientId")
    class ExtractClientId {

        @Test @DisplayName("возвращает clientId из явного claim")
        void explicitClaim() {
            Long id = jwtUtil.extractClientId(bearer(userToken(42L)));
            assertThat(id).isEqualTo(42L);
        }

        @Test @DisplayName("fallback на subject если claim clientId отсутствует")
        void fallbackToSubject() {
            // adminToken() не имеет claim clientId, subject = "admin" → NumberFormatException
            // Проверяем токен с numeric subject без явного clientId claim
            Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
            String token = Jwts.builder()
                    .setSubject("99")
                    .claim("authorities", List.of("ROLE_USER"))
                    .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
            assertThat(jwtUtil.extractClientId(bearer(token))).isEqualTo(99L);
        }

        @Test @DisplayName("истёкший токен → JwtException")
        void expired_throws() {
            assertThatThrownBy(() -> jwtUtil.extractClientId(bearer(expiredToken())))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("невалидная подпись → JwtException")
        void wrongSignature_throws() {
            String token = bearer(userToken(42L)).replace(".", "X.");
            assertThatThrownBy(() -> jwtUtil.extractClientId(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("отсутствует заголовок → JwtException")
        void missingHeader_throws() {
            assertThatThrownBy(() -> jwtUtil.extractClientId(null))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("заголовок без Bearer → JwtException")
        void noBearer_throws() {
            assertThatThrownBy(() -> jwtUtil.extractClientId(userToken(42L)))
                    .isInstanceOf(JwtException.class);
        }
    }

    // ── extractRoles ──────────────────────────────────────────────────────────

    @Nested @DisplayName("extractRoles")
    class ExtractRoles {

        @Test @DisplayName("ROLE_USER токен → [ROLE_USER]")
        void userRoles() {
            assertThat(jwtUtil.extractRoles(bearer(userToken(42L))))
                    .containsExactly("ROLE_USER");
        }

        @Test @DisplayName("ROLE_ADMIN токен → [ROLE_ADMIN]")
        void adminRoles() {
            assertThat(jwtUtil.extractRoles(bearer(adminToken())))
                    .containsExactly("ROLE_ADMIN");
        }

        @Test @DisplayName("токен без authorities → пустой список")
        void noAuthorities_emptyList() {
            Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
            String token = Jwts.builder()
                    .setSubject("42")
                    .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
            assertThat(jwtUtil.extractRoles(bearer(token))).isEmpty();
        }
    }

    // ── hasRole ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("hasRole")
    class HasRole {

        @Test @DisplayName("ROLE_ADMIN токен → hasRole(ROLE_ADMIN) = true")
        void adminHasAdminRole() {
            assertThat(jwtUtil.hasRole(bearer(adminToken()), "ROLE_ADMIN")).isTrue();
        }

        @Test @DisplayName("ROLE_USER токен → hasRole(ROLE_ADMIN) = false")
        void userHasNoAdminRole() {
            assertThat(jwtUtil.hasRole(bearer(userToken(42L)), "ROLE_ADMIN")).isFalse();
        }

        @Test @DisplayName("ROLE_USER токен → hasRole(ROLE_USER) = true")
        void userHasUserRole() {
            assertThat(jwtUtil.hasRole(bearer(userToken(42L)), "ROLE_USER")).isTrue();
        }
    }

    // ── hasBearerToken ────────────────────────────────────────────────────────

    @Nested @DisplayName("hasBearerToken")
    class HasBearerToken {

        @Test @DisplayName("Bearer-заголовок → true")
        void valid() {
            assertThat(jwtUtil.hasBearerToken("Bearer some.token.here")).isTrue();
        }

        @Test @DisplayName("null → false")
        void nullHeader() {
            assertThat(jwtUtil.hasBearerToken(null)).isFalse();
        }

        @Test @DisplayName("без Bearer-префикса → false")
        void noBearer() {
            assertThat(jwtUtil.hasBearerToken("some.token")).isFalse();
        }

        @Test @DisplayName("пустая строка → false")
        void emptyString() {
            assertThat(jwtUtil.hasBearerToken("")).isFalse();
        }
    }
}
