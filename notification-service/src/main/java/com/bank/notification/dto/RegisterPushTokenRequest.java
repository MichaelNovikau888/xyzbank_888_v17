package com.bank.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на регистрацию push-токена мобильного устройства.
 * Используется в POST /api/v1/push/register.
 */
public class RegisterPushTokenRequest {

    @NotBlank
    public String clientId;

    @NotBlank
    public String pushToken;

    /** "ios" или "android" */
    @NotBlank
    public String deviceType;

    public RegisterPushTokenRequest() {}

    public RegisterPushTokenRequest(String clientId, String pushToken, String deviceType) {
        this.clientId   = clientId;
        this.pushToken  = pushToken;
        this.deviceType = deviceType;
    }
}
