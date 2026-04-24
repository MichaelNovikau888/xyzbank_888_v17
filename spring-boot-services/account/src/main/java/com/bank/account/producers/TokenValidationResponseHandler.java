package com.bank.account.producers;

import com.bank.account.dto.KafkaResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TokenValidationResponseHandler {
    private final Map<String, CompletableFuture<Boolean>> tokenValidationRequests = new ConcurrentHashMap<>();

    public CompletableFuture<Boolean> createValidationFuture(String requestId) {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        tokenValidationRequests.put(requestId, future);
        return future;
    }

    public void handleValidationResponse(KafkaResponse response) {
        final CompletableFuture<Boolean> future = tokenValidationRequests.remove(response.getRequestId());
        if (future != null) {
            future.complete(response.isSuccess());
        } else {
            log.warn("Received response for unknown request ID: {}", response.getRequestId());
        }
    }
}
