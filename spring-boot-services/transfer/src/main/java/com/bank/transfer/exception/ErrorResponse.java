package com.bank.transfer.exception;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ErrorResponse {
    private LocalDateTime occurredAt;
    private int status;
    private String error;
    private String message;
    private List<String> errors;
    private boolean success;
    private String requestId; // Добавляем поле requestId

    public ErrorResponse(int status, String error, String message) {
        this.occurredAt = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.errors = new ArrayList<>();
        this.success = false;
        this.requestId = null; // Пока нет requestId
    }

    public ErrorResponse(int status, String error, String message, List<String> errors) {
        this(status, error, message);
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public ErrorResponse(int status, String error, String message, String requestId) {
        this(status, error, message);
        this.requestId = requestId;
    }

    public ErrorResponse(int status, String error, String message, List<String> errors, String requestId) {
        this(status, error, message, errors);
        this.requestId = requestId;
    }
}
