package com.bank.profile.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Data;

/**
 * Бизнес-метрики profile-service.
 /
 * Ключевые алерты:
 *   CRITICAL: rate(profile_created_total[5m]) == 0 за 15 мин при наличии
 *             входящих команд — регистрация сломана
 *   WARNING:  rate(profile_idempotent_skipped_total[5m]) > 20
 *             — много повторных Kafka-команд от upstream
 */
@Data
@ApplicationScoped
public class ProfileMetrics {

    @Inject
    MeterRegistry registry;

    private Counter profilesCreated;
    private Counter profilesUpdated;
    private Counter profilesDeleted;
    private Counter profileIdempotentSkipped;

    private Counter accountDetailsCreated;
    private Counter accountDetailsUpdated;
    private Counter accountDetailsDeleted;
    private Counter accountDetailsIdempotentSkipped;

    @PostConstruct
    void init() {
        this.profilesCreated = Counter.builder("profile_created_total")
                .description("Profiles successfully created")
                .register(registry);

        this.profilesUpdated = Counter.builder("profile_updated_total")
                .description("Profiles successfully updated")
                .register(registry);

        this.profilesDeleted = Counter.builder("profile_deleted_total")
                .description("Profiles successfully deleted")
                .register(registry);

        this.profileIdempotentSkipped = Counter.builder("profile_idempotent_skipped_total")
                .description("Duplicate profile Kafka commands skipped")
                .register(registry);

        this.accountDetailsCreated = Counter.builder("account_details_created_total")
                .description("AccountDetails records successfully created")
                .register(registry);

        this.accountDetailsUpdated = Counter.builder("account_details_updated_total")
                .description("AccountDetails records successfully updated")
                .register(registry);

        this.accountDetailsDeleted = Counter.builder("account_details_deleted_total")
                .description("AccountDetails records successfully deleted")
                .register(registry);

        this.accountDetailsIdempotentSkipped = Counter.builder("account_details_idempotent_skipped_total")
                .description("Duplicate account_details Kafka commands skipped")
                .register(registry);
    }
}
