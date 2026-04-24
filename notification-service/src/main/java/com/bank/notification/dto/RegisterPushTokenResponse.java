package com.bank.notification.dto;

/**
 * Ответ на успешную регистрацию push-токена.
 */
public class RegisterPushTokenResponse {

    public String message;
    public String clientId;

    public RegisterPushTokenResponse() {}

    public RegisterPushTokenResponse(String message, String clientId) {
        this.message  = message;
        this.clientId = clientId;
    }
}
