package com.bank.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import com.bank.notification.event.CardNotificationEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.PushNotificationService;

import java.time.Duration;

/**
 * Kafka-консьюмер событий жизненного цикла банковских карт.
 */
 /**
 * Слушает топики: card.created, card.blocked, card.unblocked, card.limit.changed
 */
 /**
 * В application.properties все четыре топика объединены в один канал «card-events»
 * через параметр topics (comma-separated).
 */
 /**
 * Логика по eventType:
 *   CREATED      → push «Ваша карта **** XXXX выпущена»
 *   BLOCKED      → push «Карта **** XXXX заблокирована»
 *   UNBLOCKED    → push «Карта **** XXXX разблокирована»
 *   LIMIT_CHANGED → push «Изменён лимит по карте **** XXXX»
 */
 /**
 * Idempotency: Redis dedup-ключ «notif:card:{cardId}:{eventType}» TTL=24ч.
 * Пуши не дублируются при повторной доставке сообщения.
 */
@ApplicationScoped
public class CardNotificationConsumer {

    private static final Logger LOG = Logger.getLogger(CardNotificationConsumer.class);
    private static final Duration DEDUP_TTL    = Duration.ofHours(24);
    private static final String   DEDUP_PREFIX = "notif:card:";

    @Inject PushNotificationService pushService;
    @Inject RedisDataSource         redisDataSource;
    @Inject NotificationMetrics     metrics;
    @Inject ObjectMapper            objectMapper;

    private ValueCommands<String, String> redis;

    @PostConstruct
    void init() {
        redis = redisDataSource.value(String.class);
    }

    @Incoming("card-events")
    @Blocking
    @ActivateRequestContext
    public void handleCardEvent(String payload) {

        CardNotificationEvent event;
        try {
            event = objectMapper.readValue(payload, CardNotificationEvent.class);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to deserialize CardNotificationEvent: %s", payload);
            metrics.getEmailsFailed().increment();
            return;
        }

        if (event.cardId == null || event.eventType == null) {
            LOG.warnf("CardNotificationEvent missing required fields: %s", payload);
            return;
        }

        String dedupKey = DEDUP_PREFIX + event.cardId + ":" + event.eventType;

        if (isDuplicate(dedupKey)) {
            LOG.warnf("Idempotency: card_id=%d event=%s already processed, skipping",
                      event.cardId, event.eventType);
            metrics.getIdempotentSkipped().increment();
            return;
        }

        LOG.infof("[CARD %s] card_id=%d masked=%s client_id=%s",
                  event.eventType, event.cardId, event.maskedCardNumber, event.clientId);

        try {
            processEvent(event);
            markProcessed(dedupKey);
            metrics.getEmailsSentStatusChanged().increment();
        } catch (Exception e) {
            LOG.errorf(e, "[CARD %s] Failed for card_id=%d", event.eventType, event.cardId);
            metrics.getEmailsFailed().increment();
            throw new RuntimeException("Card notification failed", e);
        }
    }

    // ── Routing by eventType ──────────────────────────────────────────────────

    private void processEvent(CardNotificationEvent event) {

        // clientId может быть null если account-service старой версии.
        // Fallback: accountId.toString() — хотя бы что-то для lookup токена.
        String clientId = event.clientId != null
                ? event.clientId
                : (event.accountId != null ? event.accountId.toString() : "unknown");

        switch (event.eventType) {

            case "CREATED" ->
                pushService.sendCardCreatedPush(clientId, event.cardId, event.maskedCardNumber);

            case "BLOCKED" ->
                pushService.sendCardBlockedPush(clientId, event.cardId, event.maskedCardNumber);

            case "UNBLOCKED" ->
                pushService.sendCardUnblockedPush(clientId, event.cardId, event.maskedCardNumber);

            case "LIMIT_CHANGED" ->
                pushService.sendCardLimitChangedPush(clientId, event.cardId,
                                                     event.maskedCardNumber,
                                                     event.dailyLimit, event.monthlyLimit);

            default ->
                LOG.warnf("Unknown card event type '%s' for card_id=%d",
                          event.eventType, event.cardId);
        }
    }

    // ── Redis dedup ───────────────────────────────────────────────────────────

    private boolean isDuplicate(String key) {
        try {
            return redis.get(key) != null;
        } catch (Exception e) {
            LOG.errorf(e, "Redis check failed for key=%s, proceeding without dedup", key);
            return false;
        }
    }

    private void markProcessed(String key) {
        try {
            redis.setex(key, DEDUP_TTL.getSeconds(), "1");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to mark key=%s as processed in Redis", key);
        }
    }
}
