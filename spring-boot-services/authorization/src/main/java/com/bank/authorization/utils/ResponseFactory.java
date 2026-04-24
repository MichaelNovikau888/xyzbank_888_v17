package com.bank.authorization.utils;

import com.bank.authorization.dto.KafkaResponse;
import org.springframework.stereotype.Component;

@Component
public class ResponseFactory {

    public KafkaResponse createSuccessResponse(String requestId, String message, Object data) {
        KafkaResponse response = new KafkaResponse();
        response.setRequestId(requestId);
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public KafkaResponse createErrorResponse(String requestId, String errorMessage) {
        KafkaResponse response = new KafkaResponse();
        response.setRequestId(requestId);
        response.setSuccess(false);
        response.setMessage(errorMessage);
        return response;
    }
}
