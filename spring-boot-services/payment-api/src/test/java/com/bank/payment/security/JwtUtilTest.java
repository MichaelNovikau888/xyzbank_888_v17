package com.bank.payment.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты JwtUtil (payment-api).
 */
 /**
 * Покрывает:
 *   1. Валидный токен с claim «clientId» → возвращает clientId
 *   2. Валидный токен без claim «clientId» → fallback на subject
 *   3. Просроченный токен → JwtException
 *   4. Неверная подпись (другой секрет) → JwtException
 *   5. Заголовок без prefix «Bearer » → JwtException
 *   6. Пустой заголовок → JwtException
 *   7. null заголовок → JwtException
 *   8. hasBearerToken(): корректная проверка наличия токена
 */
@DisplayName("JwtUtil — unit tests")
class JwtUtilTest {

    // Base64-encoded тестовый секрет (>= 32 байта после декодирования)
    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzISE=";

    private static final String WRONG_SECRET =
            "d3Jvbmctc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcyE=";

    private static final String CLIENT_ID = "client-42";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", TEST_SECRET);
    }

    // ── 1. Валидный токен с claim «clientId» ─────────────────────────────────

    @Test
    @DisplayName("1. Валидный токен с claim clientId → возвращает clientId")
    void extractClientId_validTokenWithClientIdClaim_returnsClientId() {
        String token = buildToken(CLIENT_ID, CLIENT_ID, validExpiry());

        String result = jwtUtil.extractClientId("Bearer " + token);

        assertThat(result).isEqualTo(CLIENT_ID);
    }

    // ── 2. Fallback на subject ────────────────────────────────────────────────

    @Test
    @DisplayName("2. Токен без claim clientId → fallback на subject")
    void extractClientId_validTokenWithoutClientIdClaim_fallsBackToSubject() {
        // Генерируем токен БЕЗ claim "clientId"
        String token = Jwts.builder()
                .setSubject(CLIENT_ID)
                .setIssuedAt(new Date())
                .setExpiration(validExpiry())
                .signWith(signingKey(TEST_SECRET))
                .compact();

        String result = jwtUtil.extractClientId("Bearer " + token);

        assertThat(result).isEqualTo(CLIENT_ID);
    }

    // ── 3. Просроченный токен ─────────────────────────────────────────────────

    @Test
    @DisplayName("3. Просроченный токен → JwtException")
    void extractClientId_expiredToken_throwsJwtException() {
        Date pastExpiry = new Date(System.currentTimeMillis() - 60_000); // -1 минута
        String token = buildToken(CLIENT_ID, CLIENT_ID, pastExpiry);

        assertThatThrownBy(() -> jwtUtil.extractClientId("Bearer " + token))
                .isInstanceOf(JwtException.class);
    }

    // ── 4. Неверная подпись ───────────────────────────────────────────────────

    @Test
    @DisplayName("4. Токен с чужой подписью → JwtException")
    void extractClientId_wrongSignature_throwsJwtException() {
        // Подписан ДРУГИМ секретом
        String token = Jwts.builder()
                .setSubject(CLIENT_ID)
                .claim("clientId", CLIENT_ID)
                .setIssuedAt(new Date())
                .setExpiration(validExpiry())
                .signWith(signingKey(WRONG_SECRET))
                .compact();

        assertThatThrownBy(() -> jwtUtil.extractClientId("Bearer " + token))
                .isInstanceOf(JwtException.class);
    }

    // ── 5. Заголовок без «Bearer » ────────────────────────────────────────────

    @Test
    @DisplayName("5. Заголовок без prefix 'Bearer ' → JwtException")
    void extractClientId_noBearerPrefix_throwsJwtException() {
        String token = buildToken(CLIENT_ID, CLIENT_ID, validExpiry());

        assertThatThrownBy(() -> jwtUtil.extractClientId(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Bearer");
    }

    // ── 6. Пустой заголовок ───────────────────────────────────────────────────

    @Test
    @DisplayName("6. Пустой заголовок → JwtException")
    void extractClientId_blankHeader_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtil.extractClientId("  "))
                .isInstanceOf(JwtException.class);
    }

    // ── 7. null заголовок ────────────────────────────────────────────────────

    @Test
    @DisplayName("7. null заголовок → JwtException")
    void extractClientId_nullHeader_throwsJwtException() {
        assertThatThrownBy(() -> jwtUtil.extractClientId(null))
                .isInstanceOf(JwtException.class);
    }

    // ── 8. hasBearerToken ────────────────────────────────────────────────────

    @Test
    @DisplayName("8a. hasBearerToken: заголовок с Bearer → true")
    void hasBearerToken_withBearer_returnsTrue() {
        assertThat(jwtUtil.hasBearerToken("Bearer sometoken")).isTrue();
    }

    @Test
    @DisplayName("8b. hasBearerToken: пустой заголовок → false")
    void hasBearerToken_blankHeader_returnsFalse() {
        assertThat(jwtUtil.hasBearerToken("")).isFalse();
        assertThat(jwtUtil.hasBearerToken(null)).isFalse();
        assertThat(jwtUtil.hasBearerToken("Basic abc123")).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildToken(String subject, String clientId, Date expiry) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("clientId", clientId)
                .setIssuedAt(new Date())
                .setExpiration(expiry)
                .signWith(signingKey(TEST_SECRET))
                .compact();
    }

    private Date validExpiry() {
        return new Date(System.currentTimeMillis() + 3_600_000); // +1 час
    }

    private Key signingKey(String secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
