package com.bank.authorization.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Событие «пользователь зарегистрировался».
 */
 /**
 * <p>Публикуется в топик {@code auth.user.registered}.
 * Потребители:
 * <ul>
 *   <li>notification-service — отправляет welcome email с ссылкой верификации</li>
 *   <li>profile-service — создаёт профиль клиента</li>
 *   <li>account-service — создаёт первичный счёт</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    private Long   userId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private LocalDateTime registeredAt;
}
