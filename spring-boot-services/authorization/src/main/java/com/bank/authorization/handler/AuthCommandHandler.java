package com.bank.authorization.handler;

import com.bank.authorization.dto.AuthRequest;
import com.bank.authorization.dto.AuthResponse;
import com.bank.authorization.dto.KafkaRequest;
import com.bank.authorization.dto.KafkaResponse;
import com.bank.authorization.utils.JwtTokenUtil;
import com.bank.authorization.utils.ResponseFactory;
import com.bank.authorization.metrics.AuthMetrics;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCommandHandler {

    @Value("${topics.auth_login_response}")
    private String authLoginResponseTopic;

    @Value("${topics.auth_validate_response}")
    private String authValidateResponseTopic;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final KafkaTemplate<String, KafkaResponse> kafkaTemplate;
    private final ResponseFactory responseFactory;
    private final AuthMetrics authMetrics;

    @Timed("kafka_handleLogin")
    public void handleLogin(AuthRequest request) {
        log.info(">>> handleLogin called for profileId: {}", request.getProfileId());
        KafkaResponse response;
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getProfileId(), request.getPassword())
            );

            String jwt = jwtTokenUtil.generateToken(
                    String.valueOf(request.getProfileId()), authentication.getAuthorities()
            );

            AuthResponse authResponse = new AuthResponse();
            authResponse.setJwt(jwt);
            authResponse.setAuthorities(authentication.getAuthorities()
                    .stream()
                    .map(a -> a.getAuthority())
                    .toList());

            authMetrics.getLoginSuccess().increment();
            response = responseFactory.createSuccessResponse(request.getRequestId(), "Login successful", authResponse);
        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for profileId: {}", request.getProfileId());
            // ── Исправлен баг: счётчик неудачных логинов теперь инкрементируется ──
            authMetrics.getLoginFailed().increment();
            response = responseFactory.createErrorResponse(request.getRequestId(), "Invalid username or password");
        }

        kafkaTemplate.send(authLoginResponseTopic, response);
    }

    @Timed("kafka_handleTokenValidation")
    public void handleTokenValidation(KafkaRequest request) {
        KafkaResponse response;
        try {
            boolean isValid = jwtTokenUtil.validateToken(request.getJwtToken());

            if (isValid) {
                List<String> authorities = jwtTokenUtil.getAuthoritiesFromToken(request.getJwtToken());
                authMetrics.getTokenValid().increment();
                response = responseFactory.createSuccessResponse(
                        request.getRequestId(),
                        "Token validation completed",
                        authorities
                );
            } else {
                authMetrics.getTokenInvalid().increment();
                response = responseFactory.createErrorResponse(
                        request.getRequestId(),
                        "Invalid JWT token"
                );
            }
        } catch (ExpiredJwtException e) {
            log.warn("Token expired for requestId {}: {}", request.getRequestId(), e.getMessage());
            authMetrics.getTokenInvalid().increment();
            response = responseFactory.createErrorResponse(request.getRequestId(), "token_expired");
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("Token signature invalid for requestId {}: {}", request.getRequestId(), e.getMessage());
            authMetrics.getTokenInvalid().increment();
            response = responseFactory.createErrorResponse(request.getRequestId(), "invalid_signature");
        } catch (JwtException e) {
            log.warn("Token invalid for requestId {}: {}", request.getRequestId(), e.getMessage());
            authMetrics.getTokenInvalid().increment();
            response = responseFactory.createErrorResponse(request.getRequestId(), "invalid_token");
        } catch (Exception e) {
            log.error("Unexpected error during token validation for requestId {}: {}", request.getRequestId(), e.getMessage(), e);
            authMetrics.getTokenInvalid().increment();
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error validating token");
        }

        kafkaTemplate.send(authValidateResponseTopic, response);
    }

}
