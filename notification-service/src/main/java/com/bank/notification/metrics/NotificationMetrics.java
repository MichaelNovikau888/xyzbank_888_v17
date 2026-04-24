package com.bank.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Бизнес-метрики notification-service.
 /
 * Ключевые алерты:
 *   CRITICAL: rate(notification_emails_failed_total[5m]) > 5
 *             — массовые сбои доставки писем (проблема SMTP/MailHog)
 *   WARNING:  rate(notification_idempotent_skipped_total[5m]) > 50
 *             — много дублей от Kafka (проблема с продюсером)
 *   INFO:     rate(notification_emails_sent_total[5m]) == 0 за 15 мин
 *             — нет уведомлений, хотя платежи проходят
 */
@ApplicationScoped
public class NotificationMetrics {

    @Inject
    MeterRegistry registry;

    private Counter emailsSentCreated;
    private Counter emailsSentStatusChanged;
    private Counter emailsFailed;
    private Counter idempotentSkipped;
    private Counter notificationSent;

    @PostConstruct
    void init() {
        this.emailsSentCreated = Counter.builder("notification_emails_sent_total")
                .tag("event_type", "payment_created")
                .description("Email notifications sent for payment.created events")
                .register(registry);

        this.emailsSentStatusChanged = Counter.builder("notification_emails_sent_total")
                .tag("event_type", "payment_status_changed")
                .description("Email notifications sent for payment.status_changed events")
                .register(registry);

        this.emailsFailed = Counter.builder("notification_emails_failed_total")
                .description("Email notification delivery failures")
                .register(registry);

        this.idempotentSkipped = Counter.builder("notification_idempotent_skipped_total")
                .description("Duplicate Kafka events skipped by Redis idempotency guard")
                .register(registry);
        this.notificationSent = Counter.builder("notification_sent_total")
                .tag("type", "all")
                .description("Total notifications sent")
                .register(registry);
    }

    public Counter getEmailsSentCreated()      { return emailsSentCreated; }
    public Counter getEmailsSentStatusChanged() { return emailsSentStatusChanged; }
    public Counter getEmailsFailed()            { return emailsFailed; }
    public Counter getIdempotentSkipped()       { return idempotentSkipped; }
    public Counter getNotificationSent()         { return notificationSent; }
}
