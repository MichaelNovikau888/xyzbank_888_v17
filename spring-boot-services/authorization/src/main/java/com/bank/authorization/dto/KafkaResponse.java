package com.bank.authorization.dto;

import lombok.Data;

@Data
public class KafkaResponse {
    private String requestId;
    private boolean success;
    private String message;
    private Object data;
}
