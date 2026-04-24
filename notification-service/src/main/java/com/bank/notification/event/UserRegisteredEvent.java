package com.bank.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Событие «пользователь зарегистрировался».
 */
 /**
 * <p>Публикуется authorization-service в топик {@code auth.user.registered}.
 * Потребитель: {@link com.bank.notification.consumer.RegistrationNotificationConsumer}
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegisteredEvent {

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("email")
    private String email;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @JsonProperty("registeredAt")
    private LocalDateTime registeredAt;
}
