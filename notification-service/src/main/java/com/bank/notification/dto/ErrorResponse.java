package com.bank.notification.dto;

/**
 * Стандартный ответ об ошибке для REST-ресурсов notification-service.
 */
public class ErrorResponse {

    public String error;

    public ErrorResponse() {}

    public ErrorResponse(String error) {
        this.error = error;
    }
}
