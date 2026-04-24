package com.bank.notification.consumer;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import com.bank.notification.event.PaymentCreatedEvent;
import com.bank.notification.event.PaymentStatusChangedEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
import com.bank.notification.service.NotificationRecordService;
import com.bank.notification.service.PaymentNotificationFormatter;
import com.bank.notification.service.PushNotificationService;

import java.time.Duration;

/**
 * Kafka-консьюмер платёжных событий.
 */
 /**
 * Для каждого события:
 *   1. Idempotency guard через Redis (dedup-ключ с TTL=24ч)
 *   2. Push-уведомление (PushNotificationService → FCM) — все статусы
 *   3. Email-уведомление (EmailService → Mailer)
 *      — CREATED: всегда
 *      — STATUS_CHANGED: все статусы
 */
 /**
 * Idempotency: at-most-once для уведомлений (дубль письма хуже, чем редкий пропуск).
 * Dedup-ключ: "notif:processed:{paymentId}:{eventType}" TTL=24ч.
 */
@ApplicationScoped
public class NotificationConsumer {

    private static final Logger LOG = Logger.getLogger(NotificationConsumer.class);
    private static final Duration DEDUP_TTL = Duration.ofHours(24);
    private static final String DEDUP_PREFIX = "notif:processed:";

    @Inject EmailService            emailService;
    @Inject PushNotificationService        pushService;
    @Inject PaymentNotificationFormatter   formatter;
    @Inject RedisDataSource         redisDataSource;
    @Inject NotificationRecordService recordService;
    @Inject NotificationMetrics     metrics;

    private ValueCommands<String, String> redis;

    @jakarta.annotation.PostConstruct
    void init() {
        redis = redisDataSource.value(String.class);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    @Incoming("payment-created")
    @Blocking
    @ActivateRequestContext
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        String dedupKey = DEDUP_PREFIX + event.paymentId + ":CREATED";

        if (isDuplicate(dedupKey)) {
            LOG.warnf("Idempotency: payment_id=%d CREATED already sent, skipping", event.paymentId);
            metrics.getIdempotentSkipped().increment();
            return;
        }

        LOG.infof("[CREATED] payment_id=%d, client_id=%s", event.paymentId, event.clientId);
        try {
            // 1. Push — банковский формат (OPLATA / KARTA # / дата / мерчант)
            // cardLastFour: последние 4 цифры счёта из PaymentCreatedEvent (v2)
            String pushBody = formatter.formatPaymentCreatedPush(
                    event,
                    event.cardLastFour,  // cardLastFour: последние 4 цифры recipientAccount
                    null,                // merchantName: в v1 не передаётся — используем recipientAccount
                    null);               // balance: требует запроса к account-service — TODO v3
            pushService.sendPaymentCreatedPush(
                    event.clientId, event.paymentId, event.amount, event.currency,
                    pushBody);

            // 2. Email
            emailService.sendPaymentNotification(
                    event.clientId, event.paymentId,
                    event.amount, event.currency, event.recipientAccount);

            markProcessed(dedupKey);
            metrics.getEmailsSentCreated().increment();
            LOG.infof("[CREATED] Notifications sent for payment_id=%d", event.paymentId);
        } catch (Exception e) {
            LOG.errorf(e, "[CREATED] Failed for payment_id=%d", event.paymentId);
            metrics.getEmailsFailed().increment();
            throw new RuntimeException("Notification failed", e);
        }
    }

    @Incoming("payment-status-changed")
    @Blocking
    @ActivateRequestContext
    public void handlePaymentStatusChanged(PaymentStatusChangedEvent event) {
        String dedupKey = DEDUP_PREFIX + event.paymentId + ":STATUS_CHANGED:" + event.newStatus;

        if (isDuplicate(dedupKey)) {
            LOG.warnf("Idempotency: payment_id=%d status=%s already sent, skipping",
                      event.paymentId, event.newStatus);
            metrics.getIdempotentSkipped().increment();
            return;
        }

        LOG.infof("[STATUS_CHANGED] payment_id=%d %s→%s",
                  event.paymentId, event.oldStatus, event.newStatus);
        try {
            // 1. Push — банковский формат по статусу
            String statusPushBody = formatter.formatPaymentStatusPush(event, null);
            pushService.sendPaymentStatusChangedPush(
                    event.clientId, event.paymentId, event.newStatus,
                    event.reason, statusPushBody);

            // 2. Email — все статусы (шаблоны для PROCESSING тоже есть)
            emailService.sendPaymentStatusChanged(
                    event.clientId, event.paymentId,
                    event.amount, event.currency, event.recipientAccount,
                    event.newStatus, event.reason);

            // 3. Запись в БД только для финальных статусов: COMPLETED, FAILED, CANCELLED
            recordService.saveIfFinal(
                    event.paymentId, event.clientId, event.newStatus,
                    event.amount, event.currency, event.recipientAccount, event.reason);

            markProcessed(dedupKey);
            metrics.getEmailsSentStatusChanged().increment();
            LOG.infof("[STATUS_CHANGED] Notifications sent for payment_id=%d status=%s",
                      event.paymentId, event.newStatus);
        } catch (Exception e) {
            LOG.errorf(e, "[STATUS_CHANGED] Failed for payment_id=%d", event.paymentId);
            metrics.getEmailsFailed().increment();
            throw new RuntimeException("Status notification failed", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
