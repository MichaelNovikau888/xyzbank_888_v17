package com.bank.notification.consumer;

import com.bank.notification.event.UserRegisteredEvent;
import com.bank.notification.metrics.NotificationMetrics;
import com.bank.notification.service.EmailService;
import com.bank.notification.service.PushNotificationService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Kafka-консьюмер событий регистрации пользователей.
 */
 /**
 * <p>Слушает топик: {@code auth.user.registered}
 */
 /**
 * <p>При получении события:
 * <ol>
 *   <li>Idempotency guard через Redis (dedup TTL=24ч)</li>
 *   <li>Отправка welcome email с приветствием</li>
 *   <li>Push-уведомление «Добро пожаловать» (если устройство зарегистрировано)</li>
 * </ol>
 */
 /**
 * <p>Ошибки не пробрасываются наверх — логируются, SmallRye направляет в DLQ.
 */
@ApplicationScoped
public class RegistrationNotificationConsumer {

    private static final Logger   LOG          = Logger.getLogger(RegistrationNotificationConsumer.class);
    private static final Duration DEDUP_TTL    = Duration.ofHours(24);
    private static final String   DEDUP_PREFIX = "notif:registration:";

    @Inject EmailService            emailService;
    @Inject PushNotificationService pushService;
    @Inject RedisDataSource         redisDataSource;
    @Inject NotificationMetrics     metrics;

    private ValueCommands<String, String> redis;

    @jakarta.annotation.PostConstruct
    void init() {
        redis = redisDataSource.value(String.class);
    }

    @Incoming("auth-user-registered")
    @Blocking
    @ActivateRequestContext
    public void onUserRegistered(UserRegisteredEvent event) {

        String dedupKey = DEDUP_PREFIX + event.getUserId();

        if (isDuplicate(dedupKey)) {
            LOG.warnf("Idempotency: registration userId=%d already processed, skipping",
                      event.getUserId());
            metrics.getIdempotentSkipped().increment();
            return;
        }

        LOG.infof("[REGISTRATION] userId=%d email=%s", event.getUserId(), event.getEmail());

        try {
            // 1. Welcome email
            emailService.sendWelcomeEmail(
                    event.getEmail(),
                    event.getFullName(),
                    event.getUserId()
            );

            // 2. Push (best-effort — у нового пользователя токена может ещё не быть)
            pushService.sendWelcomePush(
                    String.valueOf(event.getUserId()),
                    event.getFullName()
            );

            markProcessed(dedupKey);
            metrics.getNotificationSent().increment();

            LOG.infof("[REGISTRATION] Notifications sent for userId=%d", event.getUserId());

        } catch (Exception e) {
            LOG.errorf(e, "[REGISTRATION] Failed to send notifications for userId=%d",
                       event.getUserId());
            // Не пробрасываем — SmallRye направит в DLQ автоматически
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private boolean isDuplicate(String key) {
        try {
            return redis.get(key) != null;
        } catch (Exception e) {
            LOG.warnf(e, "Redis dedup check failed for key=%s — processing anyway", key);
            return false; // fail-open
        }
    }

    private void markProcessed(String key) {
        try {
            redis.setex(key, DEDUP_TTL.toSeconds(), "1");
        } catch (Exception e) {
            LOG.warnf(e, "Redis dedup mark failed for key=%s", key);
        }
    }
}
