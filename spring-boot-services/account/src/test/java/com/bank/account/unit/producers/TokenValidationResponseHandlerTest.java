package com.bank.account.unit.producers;

import com.bank.account.dto.KafkaResponse;
import com.bank.account.producers.TokenValidationResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class TokenValidationResponseHandlerTest {

    @InjectMocks
    private TokenValidationResponseHandler tokenValidationResponseHandler;

    private final Logger mockLogger = mock(Logger.class);

    @BeforeEach
    void setUp() {
        tokenValidationResponseHandler = new TokenValidationResponseHandler() {
            public Logger getLog() {
                return mockLogger;
            }
        };
    }

    @Test
    void createValidationFuture_ShouldAddNewFutureToMap() throws Exception {
        String requestId = "test-request-id";

        CompletableFuture<Boolean> future = tokenValidationResponseHandler.createValidationFuture(requestId);

        assertNotNull(future);
        assertFalse(future.isDone());

        Map<String, CompletableFuture<Boolean>> requests = getTokenValidationRequests();
        assertEquals(1, requests.size());
        assertSame(future, requests.get(requestId));
    }

    @Test
    void handleValidationResponse_ShouldCompleteExistingFuture() throws Exception {
        String requestId = "test-request-id";
        CompletableFuture<Boolean> future = tokenValidationResponseHandler.createValidationFuture(requestId);
        KafkaResponse response = new KafkaResponse(requestId, true, "Validation successful", null);

        tokenValidationResponseHandler.handleValidationResponse(response);

        assertTrue(future.isDone());
        assertTrue(future.get(1, TimeUnit.SECONDS));
        assertFalse(getTokenValidationRequests().containsKey(requestId));
    }

    @Test
    void handleValidationResponse_ShouldCompleteWithFalseForFailedValidation() throws Exception {
        String requestId = "failed-request-id";
        CompletableFuture<Boolean> future = tokenValidationResponseHandler.createValidationFuture(requestId);
        KafkaResponse response = new KafkaResponse(requestId, false, "Validation failed", null);

        tokenValidationResponseHandler.handleValidationResponse(response);

        assertTrue(future.isDone());
        assertFalse(future.get(1, TimeUnit.SECONDS));
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompletableFuture<Boolean>> getTokenValidationRequests() throws Exception {
        Field field = TokenValidationResponseHandler.class.getDeclaredField("tokenValidationRequests");
        field.setAccessible(true);
        return (Map<String, CompletableFuture<Boolean>>) field.get(tokenValidationResponseHandler);
    }
}
