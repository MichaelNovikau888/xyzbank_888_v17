package com.bank.authorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ на запрос регистрации клиента.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationResponse {

    /** Идентификатор созданного пользователя. */
    private Long userId;

    /** Email, на который отправлено письмо с верификацией. */
    private String email;

    /** Всегда true — клиент должен подтвердить email перед входом. */
    private boolean requiresEmailVerification;

    /** Сообщение для отображения пользователю. */
    private String message;
}
