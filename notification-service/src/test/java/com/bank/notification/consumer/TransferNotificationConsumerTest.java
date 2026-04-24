package com.bank.notification.consumer;

import com.bank.notification.event.TransferNotificationEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
import com.bank.notification.service.NotificationRecordService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты логики TransferNotificationConsumer (Quarkus).
 */
 /**
 * Сценарии:
 *   CREATED   → только push, email НЕ вызывается, DB НЕ пишется
 *   COMPLETED → push + email + saveIfFinal(COMPLETED)
 *   BLOCKED   → push + email с reason + saveIfFinal(BLOCKED)
 *   CANCELLED → push + email с reason + saveIfFinal(CANCELLED)
 *   дубль (Redis) → ничего не вызывается
 *   Redis недоступен → продолжаем без дедупликации
 *   ошибка push/email → счётчик failed, RuntimeException пробрасывается
 *   неизвестный статус → ни push, ни email не вызываются
 */
@QuarkusTest
class TransferNotificationConsumerTest {

    @InjectMock EmailService              emailService;
    @InjectMock PushNotificationService   pushService;
    @InjectMock NotificationRecordService recordService;
    @InjectMock NotificationMetrics       metrics;
    @InjectMock RedisDataSource           redisDataSource;

    @Inject TransferNotificationConsumer consumer;

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> redisCommands =
            (ValueCommands<String, String>) Mockito.mock(ValueCommands.class);

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = mock(Counter.class);
        when(redisDataSource.value(String.class)).thenReturn(redisCommands);
        when(metrics.getIdempotentSkipped()).thenReturn(mockCounter);
        when(metrics.getEmailsSentStatusChanged()).thenReturn(mockCounter);
        when(metrics.getEmailsFailed()).thenReturn(mockCounter);
        // По умолчанию Redis возвращает null → новое событие
        when(redisCommands.get(anyString())).thenReturn(null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CREATED — только push
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CREATED → sendTransferCreatedPush вызван, email и DB НЕ вызываются")
    void handleTransferNotification_created_onlyPush() {
        consumer.handleTransferNotification(buildEvent(1L, "CREATED", null));

        verify(pushService).sendTransferCreatedPush(
                eq("client-1"), eq(1L), eq("ACCOUNT"),
                eq(new BigDecimal("500.00")), eq("RUB"), eq("40817810099910004312"));

        verifyNoInteractions(emailService);
        verify(recordService, never()).saveIfFinal(any(), any(), any(), any(), any(), any(), any());
        verify(mockCounter).increment(); // emailsSentStatusChanged
    }

    @Test
    @DisplayName("CREATED: дубль Redis → push НЕ вызывается")
    void handleTransferNotification_createdDuplicate_skipsPush() {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleTransferNotification(buildEvent(2L, "CREATED", null));

        verifyNoInteractions(pushService, emailService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REVIEW — только push «на проверке», email и DB не вызываются
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("REVIEW → sendTransferReviewPush вызван, email и DB НЕ вызываются")
    void handleTransferNotification_review_onlyReviewPush() {
        consumer.handleTransferNotification(buildEvent(15L, "REVIEW", null));

        verify(pushService).sendTransferReviewPush(
                eq("client-15"), eq(15L),
                eq(new BigDecimal("500.00")), eq("RUB"));

        // Email не отправляется — промежуточный статус
        verifyNoInteractions(emailService);
        // В БД не пишем — REVIEW не финальный статус
        verify(recordService, never()).saveIfFinal(any(), any(), any(), any(), any(), any(), any());
        verify(mockCounter).increment(); // emailsSentStatusChanged
    }

    @Test
    @DisplayName("REVIEW: дубль Redis → push НЕ вызывается")
    void handleTransferNotification_reviewDuplicate_skipsPush() {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleTransferNotification(buildEvent(16L, "REVIEW", null));

        verifyNoInteractions(pushService, emailService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPLETED — push + email + DB
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("COMPLETED → sendTransferFinalPush + sendTransferFinalNotification + saveIfFinal")
    void handleTransferNotification_completed_pushEmailAndDb() {
        consumer.handleTransferNotification(buildEvent(3L, "COMPLETED", null));

        // Push без reason
        verify(pushService).sendTransferFinalPush(
                eq("client-3"), eq(3L), eq("ACCOUNT"),
                eq("COMPLETED"), eq(new BigDecimal("500.00")), eq("RUB"),
                isNull());

        // Email без reason
        verify(emailService).sendTransferFinalNotification(
                eq("client-3"), eq(3L), eq("ACCOUNT"), eq("COMPLETED"),
                eq(new BigDecimal("500.00")), eq("RUB"),
                eq("40817810099910004312"), anyString(),
                isNull(), any(LocalDateTime.class));

        // Запись в БД
        verify(recordService).saveIfFinal(
                eq(3L), eq("client-3"), eq("COMPLETED"),
                eq(new BigDecimal("500.00")), eq("RUB"),
                eq("40817810099910004312"), isNull());
    }

    @Test
    @DisplayName("COMPLETED: дубль Redis → push и email НЕ вызываются")
    void handleTransferNotification_completedDuplicate_skipsAll() {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleTransferNotification(buildEvent(4L, "COMPLETED", null));

        verifyNoInteractions(pushService, emailService, recordService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BLOCKED — push + email с reason + DB
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BLOCKED → push + email с reason + saveIfFinal(BLOCKED)")
    void handleTransferNotification_blocked_pushEmailWithReasonAndDb() {
        consumer.handleTransferNotification(buildEvent(5L, "BLOCKED", "High fraud risk"));

        // Push с reason
        verify(pushService).sendTransferFinalPush(
                eq("client-5"), eq(5L), eq("ACCOUNT"),
                eq("BLOCKED"), eq(new BigDecimal("500.00")), eq("RUB"),
                eq("High fraud risk"));

        // Email с reason
        verify(emailService).sendTransferFinalNotification(
                eq("client-5"), eq(5L), eq("ACCOUNT"), eq("BLOCKED"),
                eq(new BigDecimal("500.00")), eq("RUB"),
                eq("40817810099910004312"), anyString(),
                eq("High fraud risk"), any(LocalDateTime.class));

        // Запись в БД с reason
        verify(recordService).saveIfFinal(
                eq(5L), eq("client-5"), eq("BLOCKED"),
                eq(new BigDecimal("500.00")), eq("RUB"),
                eq("40817810099910004312"), eq("High fraud risk"));
    }

    @Test
    @DisplayName("BLOCKED: дубль Redis → ничего не вызывается")
    void handleTransferNotification_blockedDuplicate_skipsAll() {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleTransferNotification(buildEvent(6L, "BLOCKED", "Fraud"));

        verifyNoInteractions(pushService, emailService, recordService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CANCELLED — push + email с reason + DB
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CANCELLED → push + email с reason + saveIfFinal(CANCELLED)")
    void handleTransferNotification_cancelled_pushEmailWithReasonAndDb() {
        consumer.handleTransferNotification(buildEvent(7L, "CANCELLED", "Cancelled by user"));

        verify(pushService).sendTransferFinalPush(
                eq("client-7"), eq(7L), eq("ACCOUNT"),
                eq("CANCELLED"), eq(new BigDecimal("500.00")), eq("RUB"),
                eq("Cancelled by user"));

        verify(emailService).sendTransferFinalNotification(
                eq("client-7"), eq(7L), eq("ACCOUNT"), eq("CANCELLED"),
                any(), any(), any(), any(),
                eq("Cancelled by user"), any());

        verify(recordService).saveIfFinal(
                eq(7L), eq("client-7"), eq("CANCELLED"),
                any(), any(), any(), eq("Cancelled by user"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Redis недоступен — продолжаем без дедупликации
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Redis недоступен при CREATED → push всё равно отправляется")
    void handleTransferNotification_redisDown_proceedsWithoutDedup() {
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        consumer.handleTransferNotification(buildEvent(8L, "CREATED", null));

        // Несмотря на ошибку Redis, push должен отправиться
        verify(pushService).sendTransferCreatedPush(
                any(), any(), any(), any(), any(), any());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Redis недоступен при COMPLETED → push + email отправляются")
    void handleTransferNotification_redisDownCompleted_proceedsWithoutDedup() {
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        consumer.handleTransferNotification(buildEvent(9L, "COMPLETED", null));

        verify(pushService).sendTransferFinalPush(any(), any(), any(), any(), any(), any(), any());
        verify(emailService).sendTransferFinalNotification(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ошибка push/email — счётчик failed, исключение пробрасывается
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("COMPLETED: ошибка push → счётчик failed, RuntimeException пробрасывается")
    void handleTransferNotification_pushFails_incrementsFailedAndRethrows() {
        doThrow(new RuntimeException("FCM error"))
                .when(pushService).sendTransferFinalPush(any(), any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> consumer.handleTransferNotification(buildEvent(10L, "COMPLETED", null)));

        verify(metrics.getEmailsFailed()).increment();
        // Redis НЕ должен быть помечен как processed при ошибке
        verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("BLOCKED: ошибка email → счётчик failed, RuntimeException пробрасывается")
    void handleTransferNotification_emailFails_incrementsFailedAndRethrows() {
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendTransferFinalNotification(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> consumer.handleTransferNotification(buildEvent(11L, "BLOCKED", "Fraud")));

        verify(metrics.getEmailsFailed()).increment();
        verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Неизвестный статус
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Неизвестный статус → push и email НЕ вызываются (только warn-лог)")
    void handleTransferNotification_unknownStatus_noActionsPerformed() {
        consumer.handleTransferNotification(buildEvent(12L, "PENDING", null));

        verifyNoInteractions(pushService, emailService, recordService);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Dedup-ключ включает transferId и status → разные статусы = разные ключи
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Один transferId, разные статусы → оба обрабатываются (разные dedup-ключи)")
    void handleTransferNotification_sameIdDifferentStatuses_bothProcessed() {
        // CREATED — новый
        when(redisCommands.get(anyString())).thenReturn(null);

        consumer.handleTransferNotification(buildEvent(13L, "CREATED", null));
        consumer.handleTransferNotification(buildEvent(13L, "COMPLETED", null));

        verify(pushService).sendTransferCreatedPush(
                any(), eq(13L), any(), any(), any(), any());
        verify(pushService).sendTransferFinalPush(
                any(), eq(13L), any(), eq("COMPLETED"), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════════════════════

    private TransferNotificationEvent buildEvent(Long transferId, String status, String reason) {
        TransferNotificationEvent e = new TransferNotificationEvent();
        e.transferId       = transferId;
        e.clientId         = "client-" + transferId;
        e.transferType     = "ACCOUNT";
        e.status           = status;
        e.reason           = reason;
        e.amount           = new BigDecimal("500.00");
        e.currency         = "RUB";
        e.recipientDisplay = "40817810099910004312";
        e.purpose          = "Test purpose";
        e.occurredAt       = LocalDateTime.of(2025, 1, 15, 12, 0);
        return e;
    }
}
