package com.bank.history.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

/**
 * Бизнес-метрики history-service.
/
 * Ключевые алерты:
 *   WARNING:  rate(history_events_saved_total[5m]) == 0 за 10 мин
 *             — история не пишется, возможна потеря аудит-трейла
 *   INFO:     rate(history_idempotent_skipped_total[5m]) > 100
 *             — высокий уровень дублей (проблема с outbox relay upstream)
 *   WARNING:  rate(history_dlq_events_total[5m]) > 0
 *             — события попадают в DLQ (критические ошибки в upstream)
 */
@Getter
@ApplicationScoped
public class HistoryMetrics {

    @Inject
    MeterRegistry registry;

    private Counter eventsSaved;
    private Counter eventsIdempotentSkipped;
    private Counter dlqEventsReceived;
    private Counter auditLogsReceived;
    private Counter transferEventsReceived;
    private Counter accountEventsReceived;
    private Counter errorLogsReceived;

    @PostConstruct
    void init() {
        this.eventsSaved = Counter.builder("history_events_saved_total")
                .description("History events successfully persisted to DB")
                .register(registry);

        this.eventsIdempotentSkipped = Counter.builder("history_idempotent_skipped_total")
                .description("Duplicate events skipped by content-hash deduplication")
                .register(registry);

        this.dlqEventsReceived = Counter.builder("history_dlq_events_total")
                .description("Dead-letter queue events received and stored")
                .register(registry);

        this.auditLogsReceived = Counter.builder("history_kafka_received_total")
                .tag("source", "audit.logs")
                .description("Kafka events received from audit.logs topic")
                .register(registry);

        this.transferEventsReceived = Counter.builder("history_kafka_received_total")
                .tag("source", "transfer.events")
                .description("Kafka events received from transfer.events topic")
                .register(registry);

        this.accountEventsReceived = Counter.builder("history_kafka_received_total")
                .tag("source", "account.events")
                .description("Kafka events received from account.events topic")
                .register(registry);

        this.errorLogsReceived = Counter.builder("history_kafka_received_total")
                .tag("source", "error.logs")
                .description("Kafka events received from error.logs topic")
                .register(registry);
    }
}
