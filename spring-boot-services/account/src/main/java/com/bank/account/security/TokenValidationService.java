package com.bank.account.security;

import com.bank.account.dto.KafkaRequest;
import com.bank.account.producers.TokenValidationResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenValidationService {
    private final KafkaTemplate<String, KafkaRequest> kafkaTemplate;
    private final TokenValidationResponseHandler responseHandler;

    @Value("${kafka.topics.auth-validate}")
    private String authValidateRequestTopic;

    public void validateJwtOrThrow(String jwtToken) {
        final String requestId = UUID.randomUUID().toString();
        final CompletableFuture<Boolean> validationFuture = responseHandler.createValidationFuture(requestId);

        final KafkaRequest request = new KafkaRequest();
        request.setRequestId(requestId);
        request.setJwtToken(jwtToken);
        request.setPayload(jwtToken);

        kafkaTemplate.send(authValidateRequestTopic, request);

        try {
            final Boolean isValid = validationFuture.get(5, TimeUnit.SECONDS);
            if (!isValid) {
                throw new SecurityException("Invalid JWT token");
            }
        } catch (TimeoutException e) {
            log.error("Token validation timeout", e);
            throw new SecurityException("Token validation service unavailable");
        } catch (Exception e) {
            final String errorMessage = "Token validation failed";
            log.error(errorMessage, e);
            throw new SecurityException(errorMessage);
        }
    }
}
