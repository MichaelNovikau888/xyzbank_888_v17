package com.bank.authorization.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JwtTokenUtil {

    @Value("${app.jwt.secret-key}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

 /**
     * Ключ формируется из Base64-encoded секрета (≥ 32 байта после декодирования).
     * Секрет в application.yaml ДОЛЖЕН быть Base64-строкой, например:
     *   nawEFeYYKPTxjDZOF+eoepmPza+CLhJd+g9m3GHcvro=
 */
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

 /**
     * Валидирует токен с различением типов ошибок.
 */
 /**
     * @throws ExpiredJwtException  токен истёк
     * @throws SignatureException   подпись не совпадает
     * @throws JwtException         прочие ошибки формата/структуры
 */
    public boolean validateToken(String token) {
        // Не глотаем исключения — пробрасываем наверх, чтобы фильтр
        // мог вернуть клиенту конкретную причину ошибки (1.4).
        Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
        return true;
    }

    public String getUsernameFromToken(String token) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public List<String> getAuthoritiesFromToken(String token) {
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("authorities", List.class);
    }

 /**
     * Извлекает clientId из Authorization-заголовка вида «Bearer &lt;token&gt;».
 */
 /**
     * <p>Для обычного USER: clientId == userId (subject токена).
     * Claim «clientId» может быть явным (корпоративный клиент) или отсутствовать —
     * тогда используется subject.
 */
 /**
     * @param authHeader значение заголовка Authorization
     * @return clientId
     * @throws io.jsonwebtoken.JwtException если токен невалидный или отсутствует
 */
    public String extractClientId(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new io.jsonwebtoken.JwtException("Authorization header is missing");
        }
        if (!authHeader.startsWith("Bearer ")) {
            throw new io.jsonwebtoken.JwtException("Authorization header must start with 'Bearer '");
        }
        String token = authHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new io.jsonwebtoken.JwtException("Bearer token is empty");
        }

        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Явный claim «clientId» — для корпоративных клиентов clientId != userId
        String clientId = claims.get("clientId", String.class);
        if (clientId == null || clientId.isBlank()) {
            // Fallback: для обычных USER clientId == subject (userId)
            clientId = claims.getSubject();
        }
        if (clientId == null || clientId.isBlank()) {
            throw new io.jsonwebtoken.JwtException("JWT does not contain clientId or subject");
        }
        return clientId;
    }

    public String generateToken(String username, Collection<? extends GrantedAuthority> authorities) {
        return Jwts.builder()
                .setSubject(username)
                .claim("authorities", authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }
}
