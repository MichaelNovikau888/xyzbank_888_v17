package com.bank.account.consumers;

import com.bank.account.dto.KafkaResponse;
import com.bank.account.producers.TokenValidationResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenValidationKafkaConsumer {
    private final TokenValidationResponseHandler responseHandler;

    @KafkaListener(topics = "${kafka.topics.auth-validate-response}")
    public void handleTokenValidationResponse(KafkaResponse response) {
        if (response == null) {
            return;
        }
        log.info("Received token validation response for request ID: {}", response.getRequestId());
        responseHandler.handleValidationResponse(response);
    }
}
