package com.bank.notification.consumer;

import com.bank.notification.event.UserRegisteredEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
import com.bank.notification.service.PushNotificationService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты RegistrationNotificationConsumer (Quarkus).
 */
 /**
 * Покрывает:
 *   1. Успешная регистрация → welcome email + push отправлены, Redis помечен
 *   2. Дубль (Redis) → email и push НЕ вызываются, idempotentSkipped.increment()
 *   3. Email падает → push НЕ вызывается (исключение из email пробрасывается)
 *   4. Redis недоступен → fail-open, уведомления всё равно отправляются
 */
@QuarkusTest
@DisplayName("RegistrationNotificationConsumer — unit tests")
class RegistrationNotificationConsumerTest {

    @InjectMock EmailService            emailService;
    @InjectMock PushNotificationService pushService;
    @InjectMock NotificationMetrics     metrics;
    @InjectMock RedisDataSource         redisDataSource;

    @Inject RegistrationNotificationConsumer consumer;

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> redisCommands =
            (ValueCommands<String, String>) Mockito.mock(ValueCommands.class);

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = mock(Counter.class);
        when(redisDataSource.value(String.class)).thenReturn(redisCommands);
        when(metrics.getIdempotentSkipped()).thenReturn(mockCounter);
        when(metrics.getNotificationSent()).thenReturn(mockCounter);
        // По умолчанию Redis возвращает null → новое событие (не дубль)
        when(redisCommands.get(anyString())).thenReturn(null);
    }

    // ── 1. Успешная обработка ────────────────────────────────────────────────

    @Test
    @DisplayName("1. Новая регистрация → sendWelcomeEmail + sendWelcomePush вызваны")
    void onUserRegistered_newUser_sendsEmailAndPush() {
        consumer.onUserRegistered(buildEvent(1L, "ivan@example.com", "Иван Иванов"));

        verify(emailService).sendWelcomeEmail(
                eq("ivan@example.com"),
                eq("Иван Иванов"),
                eq(1L)
        );
        verify(pushService).sendWelcomePush(
                eq("1"),
                eq("Иван Иванов")
        );
        // Redis помечен как обработанный
        verify(redisCommands).setex(eq("notif:registration:1"), anyLong(), eq("1"));
    }

    // ── 2. Дубль через Redis ─────────────────────────────────────────────────

    @Test
    @DisplayName("2. Redis dedup: повторное событие → email и push НЕ вызываются")
    void onUserRegistered_duplicate_skipsNotifications() {
        when(redisCommands.get("notif:registration:2")).thenReturn("1");

        consumer.onUserRegistered(buildEvent(2L, "dupe@example.com", "Дубль Дублевич"));

        verifyNoInteractions(emailService);
        verifyNoInteractions(pushService);
        verify(mockCounter).increment(); // idempotentSkipped
    }

    // ── 3. Email бросает исключение ──────────────────────────────────────────

    @Test
    @DisplayName("3. Email сервис упал → push НЕ вызывается, ошибка поглощается")
    void onUserRegistered_emailFails_pushNotCalled() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendWelcomeEmail(anyString(), anyString(), eq(3L));

        // Не должен пробросить исключение — consumer поглощает ошибку (DLQ)
        consumer.onUserRegistered(buildEvent(3L, "fail@example.com", "Ошибка Ошибкова"));

        verify(emailService).sendWelcomeEmail(anyString(), anyString(), eq(3L));
        // push НЕ вызывается — исключение из email прерывает обработку
        verify(pushService, never()).sendWelcomePush(anyString(), anyString());
    }

    // ── 4. Redis недоступен → fail-open ──────────────────────────────────────

    @Test
    @DisplayName("4. Redis недоступен → fail-open: email и push всё равно отправляются")
    void onUserRegistered_redisDown_processesAnyway() {
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        consumer.onUserRegistered(buildEvent(4L, "redis@example.com", "Редис Редисов"));

        // fail-open: обработка продолжается
        verify(emailService).sendWelcomeEmail(
                eq("redis@example.com"), eq("Редис Редисов"), eq(4L));
        verify(pushService).sendWelcomePush(eq("4"), eq("Редис Редисов"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserRegisteredEvent buildEvent(Long userId, String email, String fullName) {
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setFullName(fullName);
        event.setPhoneNumber("+79161234567");
        event.setRegisteredAt(LocalDateTime.now());
        return event;
    }
}
