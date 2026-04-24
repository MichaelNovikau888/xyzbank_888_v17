package com.bank.notification.consumer;

import com.bank.notification.event.CardNotificationEvent;
import com.bank.notification.metrics.NotificationMetrics;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты логики CardNotificationConsumer (Quarkus).
 */
 /**
 * Тест вызывает handleCardEvent(String payload) — метод десериализует JSON
 * и роутит на push-сервис по eventType.
 */
 /**
 * Сценарии:
 *   CREATED       → sendCardCreatedPush() вызван
 *   BLOCKED       → sendCardBlockedPush() вызван
 *   UNBLOCKED     → sendCardUnblockedPush() вызван
 *   LIMIT_CHANGED → sendCardLimitChangedPush() вызван
 *   дубль Redis   → ничего не вызывается
 *   Redis недоступен → push всё равно отправляется (fail-open)
 *   невалидный JSON   → metrics.emailsFailed.increment(), без исключения наружу
 *   null eventType    → warn-лог, push НЕ вызывается
 *   clientId=null     → fallback на accountId.toString()
 */
@QuarkusTest
class CardNotificationConsumerTest {

    @InjectMock PushNotificationService pushService;
    @InjectMock NotificationMetrics     metrics;
    @InjectMock RedisDataSource         redisDataSource;

    @Inject CardNotificationConsumer consumer;

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
    // Роутинг по eventType
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CREATED → sendCardCreatedPush вызван с корректными аргументами")
    void handleCardEvent_created_callsSendCardCreatedPush() throws Exception {
        consumer.handleCardEvent(toJson(buildEvent(1L, "CREATED")));

        verify(pushService).sendCardCreatedPush(
                eq("client-42"), eq(1L), eq("4276 **** **** 1234"));
        verify(mockCounter).increment(); // emailsSentStatusChanged
    }

    @Test
    @DisplayName("BLOCKED → sendCardBlockedPush вызван")
    void handleCardEvent_blocked_callsSendCardBlockedPush() throws Exception {
        consumer.handleCardEvent(toJson(buildEvent(2L, "BLOCKED")));

        verify(pushService).sendCardBlockedPush(
                eq("client-42"), eq(2L), eq("4276 **** **** 1234"));
    }

    @Test
    @DisplayName("UNBLOCKED → sendCardUnblockedPush вызван")
    void handleCardEvent_unblocked_callsSendCardUnblockedPush() throws Exception {
        consumer.handleCardEvent(toJson(buildEvent(3L, "UNBLOCKED")));

        verify(pushService).sendCardUnblockedPush(
                eq("client-42"), eq(3L), eq("4276 **** **** 1234"));
    }

    @Test
    @DisplayName("LIMIT_CHANGED → sendCardLimitChangedPush вызван с лимитами")
    void handleCardEvent_limitChanged_callsSendCardLimitChangedPush() throws Exception {
        CardNotificationEvent event = buildEvent(4L, "LIMIT_CHANGED");
        event.dailyLimit   = new BigDecimal("50000");
        event.monthlyLimit = new BigDecimal("200000");

        consumer.handleCardEvent(toJson(event));

        verify(pushService).sendCardLimitChangedPush(
                eq("client-42"), eq(4L), eq("4276 **** **** 1234"),
                eq(new BigDecimal("50000")), eq(new BigDecimal("200000")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Idempotency (Redis dedup)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Дубль CREATED (Redis уже содержит ключ) → push НЕ вызывается")
    void handleCardEvent_duplicateCreated_skipsPush() throws Exception {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleCardEvent(toJson(buildEvent(5L, "CREATED")));

        verifyNoInteractions(pushService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    @Test
    @DisplayName("Дубль BLOCKED → push НЕ вызывается")
    void handleCardEvent_duplicateBlocked_skipsPush() throws Exception {
        when(redisCommands.get(anyString())).thenReturn("1");

        consumer.handleCardEvent(toJson(buildEvent(6L, "BLOCKED")));

        verifyNoInteractions(pushService);
        verify(metrics.getIdempotentSkipped()).increment();
    }

    @Test
    @DisplayName("Dedup-ключ включает eventType: CREATED и BLOCKED для одной карты — разные ключи")
    void handleCardEvent_sameCardDifferentEvents_bothProcessed() throws Exception {
        when(redisCommands.get(anyString())).thenReturn(null);

        consumer.handleCardEvent(toJson(buildEvent(7L, "CREATED")));
        consumer.handleCardEvent(toJson(buildEvent(7L, "BLOCKED")));

        verify(pushService).sendCardCreatedPush(any(), eq(7L), any());
        verify(pushService).sendCardBlockedPush(any(), eq(7L), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Redis недоступен — fail-open
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Redis недоступен при CREATED → push всё равно отправляется")
    void handleCardEvent_redisDown_proceedsWithoutDedup() throws Exception {
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        consumer.handleCardEvent(toJson(buildEvent(8L, "CREATED")));

        verify(pushService).sendCardCreatedPush(any(), eq(8L), any());
    }

    @Test
    @DisplayName("Redis недоступен при BLOCKED → push всё равно отправляется")
    void handleCardEvent_redisDownBlocked_proceedsWithoutDedup() throws Exception {
        when(redisCommands.get(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

        consumer.handleCardEvent(toJson(buildEvent(9L, "BLOCKED")));

        verify(pushService).sendCardBlockedPush(any(), eq(9L), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Невалидный JSON
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Невалидный JSON → emailsFailed.increment(), нет исключения наружу")
    void handleCardEvent_invalidJson_incrementsFailedNoException() {
        // Нет assertThrows — ошибка перехватывается внутри consumer
        consumer.handleCardEvent("{not valid json}");

        verifyNoInteractions(pushService);
        verify(metrics.getEmailsFailed()).increment();
    }

    @Test
    @DisplayName("Пустая строка → emailsFailed.increment()")
    void handleCardEvent_emptyPayload_incrementsFailed() {
        consumer.handleCardEvent("");

        verifyNoInteractions(pushService);
        verify(metrics.getEmailsFailed()).increment();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Невалидный eventType / null cardId
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Неизвестный eventType → push НЕ вызывается (только warn-лог)")
    void handleCardEvent_unknownEventType_noPushSent() throws Exception {
        consumer.handleCardEvent(toJson(buildEvent(10L, "EXPIRED")));

        verifyNoInteractions(pushService);
    }

    @Test
    @DisplayName("cardId=null → обработка прерывается, push НЕ вызывается")
    void handleCardEvent_nullCardId_skipsProcessing() throws Exception {
        CardNotificationEvent event = buildEvent(null, "CREATED");
        consumer.handleCardEvent(toJson(event));

        verifyNoInteractions(pushService);
    }

    @Test
    @DisplayName("eventType=null → обработка прерывается, push НЕ вызывается")
    void handleCardEvent_nullEventType_skipsProcessing() throws Exception {
        CardNotificationEvent event = buildEvent(11L, null);
        consumer.handleCardEvent(toJson(event));

        verifyNoInteractions(pushService);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // clientId=null → fallback на accountId.toString()
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("clientId=null → fallback: accountId используется как clientId для push")
    void handleCardEvent_nullClientId_fallbackToAccountId() throws Exception {
        CardNotificationEvent event = buildEvent(12L, "CREATED");
        event.clientId  = null;
        event.accountId = 99L;

        consumer.handleCardEvent(toJson(event));

        // Push должен быть вызван с accountId.toString() как clientId
        verify(pushService).sendCardCreatedPush(eq("99"), eq(12L), any());
    }

    @Test
    @DisplayName("clientId=null и accountId=null → clientId='unknown', push вызывается")
    void handleCardEvent_nullClientIdAndNullAccountId_usesUnknown() throws Exception {
        CardNotificationEvent event = buildEvent(13L, "BLOCKED");
        event.clientId  = null;
        event.accountId = null;

        consumer.handleCardEvent(toJson(event));

        verify(pushService).sendCardBlockedPush(eq("unknown"), eq(13L), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ошибка push → счётчик failed, RuntimeException пробрасывается
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CREATED: ошибка push → emailsFailed.increment(), RuntimeException пробрасывается")
    void handleCardEvent_pushFails_incrementsFailedAndRethrows() throws Exception {
        doThrow(new RuntimeException("FCM error"))
                .when(pushService).sendCardCreatedPush(any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> consumer.handleCardEvent(toJson(buildEvent(14L, "CREATED"))));

        verify(metrics.getEmailsFailed()).increment();
        // Redis НЕ должен быть помечен как processed при ошибке
        verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("BLOCKED: ошибка push → emailsFailed.increment(), RuntimeException пробрасывается")
    void handleCardEvent_blockedPushFails_incrementsFailedAndRethrows() throws Exception {
        doThrow(new RuntimeException("FCM timeout"))
                .when(pushService).sendCardBlockedPush(any(), any(), any());

        assertThrows(RuntimeException.class,
                () -> consumer.handleCardEvent(toJson(buildEvent(15L, "BLOCKED"))));

        verify(metrics.getEmailsFailed()).increment();
        verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // После успешной обработки Redis помечается
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Успешный CREATED → Redis dedup-ключ устанавливается")
    void handleCardEvent_success_marksRedisKey() throws Exception {
        consumer.handleCardEvent(toJson(buildEvent(16L, "CREATED")));

        verify(redisCommands).setex(
                eq("notif:card:16:CREATED"),
                anyLong(),
                eq("1"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private CardNotificationEvent buildEvent(Long cardId, String eventType) {
        CardNotificationEvent e = new CardNotificationEvent();
        e.cardId         = cardId;
        e.maskedCardNumber = "4276 **** **** 1234";
        e.accountId      = 100L;
        e.clientId       = "client-42";
        e.cardholderName = "Ivan Petrov";
        e.cardType       = "DEBIT";
        e.status         = "ACTIVE";
        e.dailyLimit     = new BigDecimal("100000");
        e.monthlyLimit   = new BigDecimal("300000");
        e.expiryDate     = "12/27";
        e.eventTime      = LocalDateTime.of(2025, 6, 1, 10, 0);
        e.eventType      = eventType;
        return e;
    }

 /**
     * Сериализует событие в JSON через Jackson (ObjectMapper доступен в CDI).
     * Используем простую ручную сериализацию чтобы не вносить зависимость на ObjectMapper в тест.
 */
    private String toJson(CardNotificationEvent e) {
        if (e == null) return "null";
        return String.format(
            "{\"cardId\":%s,\"maskedCardNumber\":%s,\"accountId\":%s," +
            "\"clientId\":%s,\"cardholderName\":%s,\"cardType\":%s," +
            "\"status\":%s,\"dailyLimit\":%s,\"monthlyLimit\":%s," +
            "\"expiryDate\":%s,\"eventTime\":%s,\"eventType\":%s}",
            jsonVal(e.cardId),
            jsonVal(e.maskedCardNumber),
            jsonVal(e.accountId),
            jsonVal(e.clientId),
            jsonVal(e.cardholderName),
            jsonVal(e.cardType),
            jsonVal(e.status),
            jsonVal(e.dailyLimit),
            jsonVal(e.monthlyLimit),
            jsonVal(e.expiryDate),
            e.eventTime != null ? "\"" + e.eventTime + "\"" : "null",
            jsonVal(e.eventType)
        );
    }

    private String jsonVal(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + s + "\"";
        return v.toString();
    }
}
