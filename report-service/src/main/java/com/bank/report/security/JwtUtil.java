package com.bank.report.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.security.Key;
import java.util.Collections;
import java.util.List;

/**
 * Утилита для разбора JWT-токенов в report-service.
 *
 * <p>report-service — downstream-сервис: он только верифицирует токены,
 * выпущенные authorization-service, и извлекает из них clientId и роли.
 * Генерация токенов здесь не нужна.
 *
 * <p>Структура токена (задаётся authorization-service):
 * <ul>
 *   <li>{@code sub} — userId (= clientId для ROLE_USER)</li>
 *   <li>claim {@code clientId} — явный clientId (для корпоративных клиентов)</li>
 *   <li>claim {@code authorities} — список ролей: {@code ["ROLE_USER"]}, {@code ["ROLE_ADMIN"]}</li>
 * </ul>
 *
 * <p>Секрет {@code app.jwt.secret-key} ДОЛЖЕН совпадать с секретом
 * в authorization-service (иначе подпись не пройдёт). В тест-профиле
 * задаётся через {@code %test.app.jwt.secret-key}.
 */
@ApplicationScoped
public class JwtUtil {

    private static final Logger LOG = Logger.getLogger(JwtUtil.class);

    @ConfigProperty(name = "app.jwt.secret-key")
    String jwtSecret;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Извлекает clientId из Authorization-заголовка вида «Bearer &lt;token&gt;».
     *
     * @param authHeader значение заголовка Authorization
     * @return clientId из claim «clientId» (или subject если claim отсутствует)
     * @throws JwtException если токен отсутствует, истёк или подпись некорректна
     */
    public Long extractClientId(String authHeader) {
        Claims claims = parseClaims(stripBearer(authHeader));
        String clientId = claims.get("clientId", String.class);
        if (clientId == null || clientId.isBlank()) {
            clientId = claims.getSubject();
        }
        if (clientId == null || clientId.isBlank()) {
            throw new JwtException("JWT does not contain clientId or subject");
        }
        try {
            return Long.parseLong(clientId);
        } catch (NumberFormatException e) {
            throw new JwtException("JWT clientId is not a valid Long: " + clientId);
        }
    }

    /**
     * Возвращает список ролей из claim «authorities».
     *
     * @param authHeader значение заголовка Authorization
     * @return список ролей, например {@code ["ROLE_ADMIN"]}; пустой список если claim отсутствует
     * @throws JwtException если токен невалидный
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String authHeader) {
        Claims claims = parseClaims(stripBearer(authHeader));
        List<String> authorities = claims.get("authorities", List.class);
        return authorities != null ? authorities : Collections.emptyList();
    }

    /**
     * Проверяет наличие роли в токене.
     *
     * @param authHeader значение заголовка Authorization
     * @param role       требуемая роль, например {@code "ROLE_ADMIN"}
     * @return {@code true} если токен содержит указанную роль
     */
    public boolean hasRole(String authHeader, String role) {
        return extractRoles(authHeader).contains(role);
    }

    /**
     * Проверяет наличие Bearer-токена в заголовке без его парсинга.
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
            LOG.warnf("JWT token expired: %s", e.getMessage());
            throw e;
        } catch (JwtException e) {
            LOG.warnf("JWT token invalid: %s", e.getMessage());
            throw e;
        }
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
