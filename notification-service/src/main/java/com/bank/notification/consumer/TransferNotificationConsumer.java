package com.bank.notification.consumer;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import com.bank.notification.event.TransferNotificationEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
import com.bank.notification.service.NotificationRecordService;
import com.bank.notification.service.PushNotificationService;

import java.time.Duration;

/**
 * Kafka-консьюмер уведомлений о переводах.
 */
 /**
 * Слушает топик: transfer.notification
 */
 /**
 * Логика по статусу:
 */
 /**
 *   CREATED   → push «Перевод создан на сумму X → получатель»
 *               email НЕ отправляется (не финальный статус, клиент ещё не знает результата)
 */
 /**
 *   REVIEW    → push «Перевод на проверке — ожидайте до 24 часов»
 *               email НЕ отправляется (промежуточный статус антифрод-проверки, 10k–50k)
 *               Запись в БД НЕ создаётся (не финальный статус)
 */
 /**
 *   COMPLETED → push «Перевод №X выполнен ✅»
 *               email подробный: сумма, куда, кому, когда, назначение
 */
 /**
 *   BLOCKED   → push «Перевод №X заблокирован ❌»
 *               email с причиной блокировки: сумма, куда, кому, причина, рекомендации
 */
 /**
 *   CANCELLED → push «Перевод №X отменён»
 *               email с причиной отмены
 */
 /**
 * Idempotency: Redis dedup-ключ «notif:transfer:{transferId}:{status}» TTL=24ч.
 */
@ApplicationScoped
public class TransferNotificationConsumer {

    private static final Logger LOG = Logger.getLogger(TransferNotificationConsumer.class);
    private static final Duration DEDUP_TTL   = Duration.ofHours(24);
    private static final String   DEDUP_PREFIX = "notif:transfer:";

    @Inject EmailService              emailService;
    @Inject PushNotificationService   pushService;
    @Inject RedisDataSource           redisDataSource;
    @Inject NotificationMetrics       metrics;

    private ValueCommands<String, String> redis;

    @jakarta.annotation.PostConstruct
    void init() {
        redis = redisDataSource.value(String.class);
    }

    @Incoming("transfer-notification")
    @Blocking
    @ActivateRequestContext
    public void handleTransferNotification(TransferNotificationEvent event) {

        String dedupKey = DEDUP_PREFIX + event.transferId + ":" + event.status;

        if (isDuplicate(dedupKey)) {
            LOG.warnf("Idempotency: transfer_id=%d status=%s already processed, skipping",
                      event.transferId, event.status);
            metrics.getIdempotentSkipped().increment();
            return;
        }

        LOG.infof("[TRANSFER %s] transfer_id=%d client_id=%s type=%s amount=%s",
                  event.status, event.transferId, event.clientId,
                  event.transferType, event.amount);

        try {
            processEvent(event);
            markProcessed(dedupKey);
            metrics.getEmailsSentStatusChanged().increment();

        } catch (Exception e) {
            LOG.errorf(e, "[TRANSFER %s] Failed for transfer_id=%d", event.status, event.transferId);
            metrics.getEmailsFailed().increment();
            throw new RuntimeException("Transfer notification failed", e);
        }
    }

    // ── Routing by status ─────────────────────────────────────────────────────

    private void processEvent(TransferNotificationEvent event) {
        switch (event.status) {

            case "CREATED" -> {
                // Только push — перевод принят, ждём антифрод-проверку
                pushService.sendTransferCreatedPush(
                        event.clientId, event.transferId,
                        event.transferType, event.amount, event.currency,
                        event.recipientDisplay);
                LOG.infof("[TRANSFER CREATED] Push sent: transfer_id=%d", event.transferId);
            }

            case "REVIEW" -> {
                // Только push — сумма 10k–50k, антифрод отправил на ручную проверку.
                // Email не отправляем: промежуточный статус, финал придёт отдельным событием.
                pushService.sendTransferReviewPush(
                        event.clientId, event.transferId, event.amount, event.currency);
                LOG.infof("[TRANSFER REVIEW] Push sent: transfer_id=%d", event.transferId);
            }

            case "COMPLETED" -> {
                // Push + подробный email
                pushService.sendTransferFinalPush(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency, null);

                emailService.sendTransferFinalNotification(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency,
                        event.recipientDisplay, event.purpose,
                        null, event.occurredAt);

                // Запись в БД
                recordService.saveIfFinal(
                        event.transferId, event.clientId, event.status,
                        event.amount, event.currency, event.recipientDisplay, null);

                LOG.infof("[TRANSFER COMPLETED] Push+Email sent: transfer_id=%d", event.transferId);
            }

            case "BLOCKED" -> {
                // Push + email с причиной блокировки
                pushService.sendTransferFinalPush(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency, event.reason);

                emailService.sendTransferFinalNotification(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency,
                        event.recipientDisplay, event.purpose,
                        event.reason, event.occurredAt);

                recordService.saveIfFinal(
                        event.transferId, event.clientId, event.status,
                        event.amount, event.currency, event.recipientDisplay, event.reason);

                LOG.infof("[TRANSFER BLOCKED] Push+Email sent: transfer_id=%d reason=%s",
                          event.transferId, event.reason);
            }

            case "CANCELLED" -> {
                // Push + email с причиной отмены
                pushService.sendTransferFinalPush(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency, event.reason);

                emailService.sendTransferFinalNotification(
                        event.clientId, event.transferId, event.transferType,
                        event.status, event.amount, event.currency,
                        event.recipientDisplay, event.purpose,
                        event.reason, event.occurredAt);

                recordService.saveIfFinal(
                        event.transferId, event.clientId, event.status,
                        event.amount, event.currency, event.recipientDisplay, event.reason);

                LOG.infof("[TRANSFER CANCELLED] Push+Email sent: transfer_id=%d", event.transferId);
            }

            default ->
                LOG.warnf("Unknown transfer status '%s' for transfer_id=%d",
                          event.status, event.transferId);
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
