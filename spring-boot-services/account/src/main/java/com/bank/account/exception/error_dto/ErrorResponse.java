package com.bank.account.exception.error_dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;

@Data
@RequiredArgsConstructor
public class ErrorResponse {
    private final String errorCode;
    private final String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Europe/Moscow")
    private Timestamp timestamp = new Timestamp(System.currentTimeMillis());
}
