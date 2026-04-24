package com.bank.publicinfo.dto;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class ErrorResponseDto {
    private final String        errorCode;
    private final String        message;
    private final LocalDateTime timestamp;

    public ErrorResponseDto(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message   = message;
        this.timestamp = LocalDateTime.now();
    }
}
