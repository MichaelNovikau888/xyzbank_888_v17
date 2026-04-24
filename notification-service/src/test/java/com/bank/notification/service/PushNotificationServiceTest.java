package com.bank.notification.service;

import com.bank.notification.client.FcmClient;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты PushNotificationService.
 */
 /**
 * FcmClient и RedisDataSource — моки.
 */
 /**
 * Покрываемые сценарии:
 *   - registerPushToken: токен сохраняется в Redis с TTL=30 дней
 *   - sendPaymentCreatedPush: push отправляется если токен есть в Redis
 *   - sendPaymentCreatedPush: токена нет → FCM не вызывается
 *   - sendPaymentCreatedPush с formattedBody → используется формат
 *   - sendPaymentStatusChangedPush: каждый статус → правильный title
 *   - sendWelcomePush: push отправляется с firstName
 *   - sendCardCreatedPush / Blocked / Unblocked / LimitChanged
 *   - sendTransferReviewPush / CreatedPush / FinalPush
 */
@QuarkusTest
@DisplayName("PushNotificationService — unit tests")
class PushNotificationServiceTest {

    @InjectMock
    RedisDataSource redisDataSource;

    @InjectMock
    @RestClient
    FcmClient fcmClient;

    @Inject
    PushNotificationService pushService;

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> redis =
            (ValueCommands<String, String>) Mockito.mock(ValueCommands.class);

    private static final String CLIENT_ID  = "client-1";
    private static final String PUSH_TOKEN = "fcm-token-abc123";

    @BeforeEach
    void setUp() {
        when(redisDataSource.value(String.class)).thenReturn(redis);
        pushService.init();
    }

    // ── registerPushToken ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("registerPushToken")
    class RegisterPushToken {

        @Test
        @DisplayName("токен сохраняется в Redis с TTL = 30 дней")
        void savesTokenWithTtl() {
            pushService.registerPushToken(CLIENT_ID, PUSH_TOKEN, "android");

            verify(redis).setex(
                    eq("push:token:" + CLIENT_ID),
                    eq(30L * 24 * 3600),
                    eq(PUSH_TOKEN)
            );
        }
    }

    // ── sendPaymentCreatedPush ────────────────────────────────────────────────

    @Nested
    @DisplayName("sendPaymentCreatedPush")
    class SendPaymentCreatedPush {

        @Test
        @DisplayName("токен есть → FCM вызывается")
        void tokenExists_fcmCalled() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);

            pushService.sendPaymentCreatedPush(CLIENT_ID, 1L,
                    new BigDecimal("500.00"), "RUB");

            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("токена нет → FCM НЕ вызывается")
        void noToken_fcmNotCalled() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(null);

            pushService.sendPaymentCreatedPush(CLIENT_ID, 1L,
                    new BigDecimal("500.00"), "RUB");

            verify(fcmClient, never()).send(any());
        }

        @Test
        @DisplayName("formattedBody не null → используется вместо дефолтного")
        void formattedBody_usedWhenNotNull() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);
            String formatted = "OPLATA 500.00 RUB\nKARTA #1234";

            pushService.sendPaymentCreatedPush(CLIENT_ID, 1L,
                    new BigDecimal("500.00"), "RUB", formatted);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(notification.get("body").toString()).contains("OPLATA");
        }

        @Test
        @DisplayName("formattedBody null → дефолтный текст используется")
        void nullFormattedBody_usesDefault() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);

            pushService.sendPaymentCreatedPush(CLIENT_ID, 42L,
                    new BigDecimal("100.00"), "RUB", null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(notification.get("body").toString()).contains("42");
        }
    }

    // ── sendPaymentStatusChangedPush ──────────────────────────────────────────

    @Nested
    @DisplayName("sendPaymentStatusChangedPush")
    class SendPaymentStatusChangedPush {

        @BeforeEach
        void tokenAvailable() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);
        }

        @Test
        @DisplayName("COMPLETED → title содержит ✅")
        void completed_titleContainsCheckmark() {
            pushService.sendPaymentStatusChangedPush(CLIENT_ID, 1L, "COMPLETED", null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("title").toString()).contains("✅");
        }

        @Test
        @DisplayName("FAILED → title содержит ❌")
        void failed_titleContainsCross() {
            pushService.sendPaymentStatusChangedPush(CLIENT_ID, 2L, "FAILED", "Fraud");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("title").toString()).contains("❌");
        }

        @Test
        @DisplayName("CANCELLED → title содержит 🚫")
        void cancelled_titleContainsBan() {
            pushService.sendPaymentStatusChangedPush(CLIENT_ID, 3L, "CANCELLED", null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("title").toString()).contains("🚫");
        }

        @Test
        @DisplayName("PROCESSING → title содержит ⏳")
        void processing_titleContainsClock() {
            pushService.sendPaymentStatusChangedPush(CLIENT_ID, 4L, "PROCESSING", null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("title").toString()).contains("⏳");
        }
    }

    // ── sendWelcomePush ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendWelcomePush")
    class SendWelcomePush {

        @Test
        @DisplayName("токен есть → FCM вызывается с именем клиента")
        void tokenExists_sendsPushWithName() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);

            pushService.sendWelcomePush(CLIENT_ID, "Иван Иванов");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("body").toString()).contains("Иван");
        }

        @Test
        @DisplayName("null fullName → fallback 'Клиент' в теле")
        void nullFullName_usesClientFallback() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);

            pushService.sendWelcomePush(CLIENT_ID, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(fcmClient).send(captor.capture());
            @SuppressWarnings("unchecked")
            Map<String, Object> n = (Map<String, Object>) captor.getValue().get("notification");
            Assertions.assertThat(n.get("body").toString()).contains("Клиент");
        }
    }

    // ── sendCardPush ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendCardCreatedPush / Blocked / Unblocked / LimitChanged")
    class SendCardPush {

        @BeforeEach
        void tokenAvailable() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);
        }

        @Test
        @DisplayName("sendCardCreatedPush → FCM вызывается")
        void cardCreated_fcmCalled() {
            pushService.sendCardCreatedPush(CLIENT_ID, 10L, "**** 5678");
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendCardBlockedPush → FCM вызывается")
        void cardBlocked_fcmCalled() {
            pushService.sendCardBlockedPush(CLIENT_ID, 10L, "**** 5678");
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendCardUnblockedPush → FCM вызывается")
        void cardUnblocked_fcmCalled() {
            pushService.sendCardUnblockedPush(CLIENT_ID, 10L, "**** 5678");
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendCardLimitChangedPush → FCM вызывается")
        void cardLimitChanged_fcmCalled() {
            pushService.sendCardLimitChangedPush(CLIENT_ID, 10L, "**** 5678",
                    new BigDecimal("10000"), new BigDecimal("100000"));
            verify(fcmClient).send(any());
        }
    }

    // ── sendTransferPush ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendTransferReviewPush / CreatedPush / FinalPush")
    class SendTransferPush {

        @BeforeEach
        void tokenAvailable() {
            when(redis.get("push:token:" + CLIENT_ID)).thenReturn(PUSH_TOKEN);
        }

        @Test
        @DisplayName("sendTransferReviewPush → FCM вызывается")
        void transferReview_fcmCalled() {
            pushService.sendTransferReviewPush(CLIENT_ID, 20L,
                    new BigDecimal("25000.00"), "RUB");
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendTransferCreatedPush → FCM вызывается")
        void transferCreated_fcmCalled() {
            pushService.sendTransferCreatedPush(CLIENT_ID, 21L, "CARD",
                    new BigDecimal("5000.00"), "RUB", "**** 1234");
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendTransferFinalPush COMPLETED → FCM вызывается")
        void transferCompleted_fcmCalled() {
            pushService.sendTransferFinalPush(CLIENT_ID, 22L, "ACCOUNT",
                    "COMPLETED", new BigDecimal("1000.00"), "RUB", null);
            verify(fcmClient).send(any());
        }

        @Test
        @DisplayName("sendTransferFinalPush BLOCKED → FCM вызывается")
        void transferBlocked_fcmCalled() {
            pushService.sendTransferFinalPush(CLIENT_ID, 23L, "PHONE",
                    "BLOCKED", new BigDecimal("60000.00"), "RUB", "Превышен лимит");
            verify(fcmClient).send(any());
        }
    }

    // ── Redis недоступен → graceful degradation ───────────────────────────────

    @Nested
    @DisplayName("Redis недоступен → FCM не вызывается")
    class RedisDown {

        @Test
        @DisplayName("Redis бросает exception → push пропускается без крэша")
        void redisDown_skipsPushGracefully() {
            when(redis.get(anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            Assertions.assertThatCode(() ->
                    pushService.sendPaymentCreatedPush(CLIENT_ID, 1L,
                            new BigDecimal("100.00"), "RUB")
            ).doesNotThrowAnyException();

            verify(fcmClient, never()).send(any());
        }
    }
}
