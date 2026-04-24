package com.bank.notification.consumer;

import com.bank.notification.event.PaymentCreatedEvent;
import com.bank.notification.event.PaymentStatusChangedEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты логики идемпотентности в NotificationConsumer.
 /
 * EmailService, Redis и NotificationMetrics — моки, чтобы тест не зависел
 * от SMTP/Redis/Kafka. Тестируем исключительно ветки if (isDuplicate).
 */
@QuarkusTest
class NotificationConsumerTest {

    @InjectMock
    EmailService emailService;

    @InjectMock
    NotificationMetrics metrics;

    @InjectMock
    RedisDataSource redisDataSource;

    @Inject
    NotificationConsumer consumer;

    // Мок-команды Redis
    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> redisCommands =
            (ValueCommands<String, String>) Mockito.mock(ValueCommands.class);

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = mock(Counter.class);
        when(redisDataSource.value(String.class)).thenReturn(redisCommands);
        when(metrics.getIdempotentSkipped()).thenReturn(mockCounter);
        when(metrics.getEmailsSentCreated()).thenReturn(mockCounter);
        when(metrics.getEmailsSentStatusChanged()).thenReturn(mockCounter);
        when(metrics.getEmailsFailed()).thenReturn(mockCounter);
    }

    // ── handlePaymentCreated ──────────────────────────────────────────────────

    @Test
    @DisplayName("handlePaymentCreated: новое событие — email отправляется, счётчик success")
    void handlePaymentCreated_newEvent_sendsEmailAndIncrementsSuccess() {
        // Redis возвращает null → новое событие
        when(redisCommands.get(anyString())).thenReturn(null);

        consumer.handlePaymentCreated(buildCreatedEvent(1L));

        verify(emailService).sendPaymentNotification(
                eq("client-1"), eq(1L), any(), eq("RUB"), anyString());
        verify(mockCounter).increment(); // emailsSentCreated
        // idempotentSkipped НЕ должен вызываться
        verify(metrics, never()).getIdempotentSkipped();
    }

    @Test
    @DisplayName("handlePaymentCreated: дубль — email НЕ отправляется, счётчик skipped")
    void handlePaymentCreated_duplicate_skipsEmailAndIncrementsSkipped() {
        // Redis возвращает значение → дубль
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handlePaymentCreated(buildCreatedEvent(2L));

        verifyNoInteractions(emailService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    @Test
    @DisplayName("handlePaymentCreated: Redis недоступен — продолжаем без дедупликации")
    void handlePaymentCreated_redisDown_proceedsWithoutDedup() {
        // Redis бросает исключение
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        consumer.handlePaymentCreated(buildCreatedEvent(3L));

        // Email всё равно должен отправиться
        verify(emailService).sendPaymentNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handlePaymentCreated: ошибка email — счётчик failed, исключение пробрасывается")
    void handlePaymentCreated_emailFails_incrementsFailedAndRethrows() {
        when(redisCommands.get(anyString())).thenReturn(null);
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendPaymentNotification(any(), any(), any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.handlePaymentCreated(buildCreatedEvent(4L)));

        verify(metrics.getEmailsFailed()).increment();
        // Ключ НЕ должен быть помечен как processed при ошибке
        verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
    }

    // ── handlePaymentStatusChanged ────────────────────────────────────────────

    @Test
    @DisplayName("handlePaymentStatusChanged: новый статус — email отправляется")
    void handlePaymentStatusChanged_newStatus_sendsEmail() {
        when(redisCommands.get(anyString())).thenReturn(null);

        consumer.handlePaymentStatusChanged(buildStatusChangedEvent(5L, "COMPLETED"));

        verify(emailService).sendPaymentStatusChanged(
                eq("client-5"), eq(5L), any(), eq("RUB"), anyString(), eq("COMPLETED"), any());
        verify(metrics.getEmailsSentStatusChanged()).increment();
    }

    @Test
    @DisplayName("handlePaymentStatusChanged: разные статусы одного платежа — оба отправляются")
    void handlePaymentStatusChanged_differentStatuses_bothSent() {
        // Разные dedup-ключи для разных статусов
        when(redisCommands.get(contains(":PROCESSING"))).thenReturn(null);
        when(redisCommands.get(contains(":COMPLETED"))).thenReturn(null);

        consumer.handlePaymentStatusChanged(buildStatusChangedEvent(6L, "PROCESSING"));
        consumer.handlePaymentStatusChanged(buildStatusChangedEvent(6L, "COMPLETED"));

        verify(emailService, times(2))
                .sendPaymentStatusChanged(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handlePaymentStatusChanged: дубль статуса — повторный email НЕ отправляется")
    void handlePaymentStatusChanged_duplicateStatus_skipped() {
        when(redisCommands.get(contains(":COMPLETED"))).thenReturn("1");

        consumer.handlePaymentStatusChanged(buildStatusChangedEvent(7L, "COMPLETED"));

        verifyNoInteractions(emailService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PaymentCreatedEvent buildCreatedEvent(Long paymentId) {
        PaymentCreatedEvent e = new PaymentCreatedEvent();
        e.paymentId = paymentId;
        e.clientId  = "client-" + paymentId;
        e.amount    = new BigDecimal("1000.00");
        e.currency  = "RUB";
        e.recipientAccount = "40817810099910004312";
        e.createdAt = LocalDateTime.now();
        return e;
    }

    private PaymentStatusChangedEvent buildStatusChangedEvent(Long paymentId, String newStatus) {
        PaymentStatusChangedEvent e = new PaymentStatusChangedEvent();
        e.paymentId = paymentId;
        e.clientId  = "client-" + paymentId;
        e.amount    = new BigDecimal("1000.00");
        e.currency  = "RUB";
        e.recipientAccount = "40817810099910004312";
        e.oldStatus = "CREATED";
        e.newStatus = newStatus;
        return e;
    }
}
