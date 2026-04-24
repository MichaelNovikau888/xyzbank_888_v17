package com.bank.payment.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;

/**
 * Утилита для извлечения clientId из Bearer JWT токена.
 */
 /**
 * <p>payment-api — downstream-сервис: он только верифицирует токены,
 * выпущенные authorization-service, и извлекает из них clientId.
 * Генерация токенов здесь не нужна.
 */
 /**
 * <p>Секрет {@code app.jwt.secret-key} ДОЛЖЕН совпадать с секретом
 * в authorization-service (иначе подпись не пройдёт).
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret-key}")
    private String jwtSecret;

    // ── Public API ────────────────────────────────────────────────────────────

 /**
     * Извлекает clientId из Authorization-заголовка вида «Bearer &lt;token&gt;».
 */
 /**
     * @param authHeader значение заголовка Authorization
     * @return clientId из claim «clientId» (или subject если claim отсутствует)
     * @throws JwtException если токен отсутствует, истёк или подпись некорректна
 */
    public String extractClientId(String authHeader) {
        String token = stripBearer(authHeader);
        Claims claims = parseClaims(token);

        // Сначала ищем явный claim "clientId", fallback на subject
        String clientId = claims.get("clientId", String.class);
        if (clientId == null || clientId.isBlank()) {
            clientId = claims.getSubject();
        }
        if (clientId == null || clientId.isBlank()) {
            throw new JwtException("JWT does not contain clientId or subject");
        }
        return clientId;
    }

 /**
     * Проверяет, что заголовок Authorization присутствует и содержит Bearer-токен.
 */
    public boolean hasBearerToken(String authHeader) {
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String stripBearer(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new JwtException("Authorization header is missing");
        }
        if (!authHeader.startsWith("Bearer ")) {
            throw new JwtException("Authorization header must start with 'Bearer '");
        }
        String token = authHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new JwtException("Bearer token is empty");
        }
        return token;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("JWT token invalid: {}", e.getMessage());
            throw e;
        }
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
