package com.bank.authorization.dto;

import lombok.Data;

@Data
public class KafkaRequest {
    private String requestId;
    private String jwtToken;
    private String payload;
}
