package com.bank.antifraud.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;

/**
 * Бизнес-метрики antifraud-service.
 * <p>
 * Ключевые алерты для Alertmanager:
 * <p>
 * CRITICAL  rate(antifraud_blocked_total[5m]) > 10
 * — аномальный всплеск блокировок: атака, ошибка порога или сбой данных
 * <p>
 * WARNING   antifraud_blocked_total / antifraud_analyzed_total > 0.30
 * — более 30% переводов блокируется, паттерн нетипичен
 * <p>
 * INFO      rate(antifraud_idempotent_skipped_total[5m]) > 50
 * — много повторных доставок от Kafka (проблема с консьюмером)
 */
@Component
@Getter
public class AntifraudMetrics {

    private final Counter cardTransfersAnalyzed;
    private final Counter phoneTransfersAnalyzed;
    private final Counter accountTransfersAnalyzed;

    private final Counter cardTransfersBlocked;
    private final Counter phoneTransfersBlocked;
    private final Counter accountTransfersBlocked;

    private final Counter idempotentSkipped;

    public AntifraudMetrics(MeterRegistry registry,
                            SuspiciousCardTransferRepository cardRepo,
                            SuspiciousPhoneTransferRepository phoneRepo,
                            SuspiciousAccountTransferRepository accountRepo) {

        this.cardTransfersAnalyzed = Counter.builder("antifraud_analyzed_total")
                .tag("type", "card").description("Card transfers analyzed").register(registry);
        this.phoneTransfersAnalyzed = Counter.builder("antifraud_analyzed_total")
                .tag("type", "phone").description("Phone transfers analyzed").register(registry);
        this.accountTransfersAnalyzed = Counter.builder("antifraud_analyzed_total")
                .tag("type", "account").description("Account transfers analyzed").register(registry);

        this.cardTransfersBlocked = Counter.builder("antifraud_blocked_total")
                .tag("type", "card").description("Card transfers blocked (amount > threshold)").register(registry);
        this.phoneTransfersBlocked = Counter.builder("antifraud_blocked_total")
                .tag("type", "phone").description("Phone transfers blocked").register(registry);
        this.accountTransfersBlocked = Counter.builder("antifraud_blocked_total")
                .tag("type", "account").description("Account transfers blocked").register(registry);

        this.idempotentSkipped = Counter.builder("antifraud_idempotent_skipped_total")
                .description("Duplicate analysis requests skipped by idempotency guard").register(registry);

        // Gauges: текущее количество заблокированных переводов в БД
        Gauge.builder("antifraud_blocked_in_db", cardRepo,
                        repo -> (double) repo.countByBlockedTrue())
                .tag("type", "card")
                .description("Blocked card transfers currently in DB")
                .register(registry);

        Gauge.builder("antifraud_blocked_in_db", phoneRepo,
                        repo -> (double) repo.countByBlockedTrue())
                .tag("type", "phone")
                .description("Blocked phone transfers currently in DB")
                .register(registry);

        Gauge.builder("antifraud_blocked_in_db", accountRepo,
                        repo -> (double) repo.countByBlockedTrue())
                .tag("type", "account")
                .description("Blocked account transfers currently in DB")
                .register(registry);
    }
}
