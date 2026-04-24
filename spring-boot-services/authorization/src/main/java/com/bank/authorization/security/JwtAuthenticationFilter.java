package com.bank.authorization.security;

import com.bank.authorization.utils.JwtTokenUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT-фильтр: извлекает токен из заголовка Authorization: Bearer <token>,
 * валидирует его и устанавливает аутентификацию в SecurityContext.
 /
 * Различает три типа ошибок токена:
 *   - ExpiredJwtException  → 401 {"error": "token_expired"}
 *   - SignatureException    → 401 {"error": "invalid_signature"}
 *   - остальное            → 401 {"error": "invalid_token"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            // Нет токена — пропускаем; Spring Security сам вернёт 401 если эндпоинт защищён
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username    = jwtTokenUtil.getUsernameFromToken(token);
            List<String> roles = jwtTokenUtil.getAuthoritiesFromToken(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("JWT expired for request {}: {}", request.getRequestURI(), e.getMessage());
            sendError(response, "token_expired");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("JWT signature invalid for request {}: {}", request.getRequestURI(), e.getMessage());
            sendError(response, "invalid_signature");
        } catch (JwtException e) {
            log.warn("JWT invalid for request {}: {}", request.getRequestURI(), e.getMessage());
            sendError(response, "invalid_token");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendError(HttpServletResponse response, String errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + errorCode + "\"}");
    }
}
