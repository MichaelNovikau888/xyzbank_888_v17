package com.bank.account.unit.consumers;

import com.bank.account.consumers.TokenValidationKafkaConsumer;
import com.bank.account.dto.KafkaResponse;
import com.bank.account.producers.TokenValidationResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class TokenValidationKafkaConsumerTest {

    @Mock
    private TokenValidationResponseHandler responseHandler;

    @InjectMocks
    private TokenValidationKafkaConsumer tokenValidationKafkaConsumer;

    @Test
    void handleTokenValidationResponse_ShouldProcessValidResponse() {
        KafkaResponse response = new KafkaResponse(
                "test-request-123",
                true,
                "Token is valid",
                null
        );

        tokenValidationKafkaConsumer.handleTokenValidationResponse(response);

        verify(responseHandler).handleValidationResponse(response);
    }

    @Test
    void handleTokenValidationResponse_ShouldProcessResponseWithMessagePayload() {
        KafkaResponse response = new KafkaResponse(
                "test-request-789",
                true,
                "Additional message",
                "Some data payload"
        );

        tokenValidationKafkaConsumer.handleTokenValidationResponse(response);

        verify(responseHandler).handleValidationResponse(response);
    }

    @Test
    void handleTokenValidationResponse_ShouldNotFailOnNullResponse() {
        tokenValidationKafkaConsumer.handleTokenValidationResponse(null);

        verifyNoMoreInteractions(responseHandler);
    }
}
