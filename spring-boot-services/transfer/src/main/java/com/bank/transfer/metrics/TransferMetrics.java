package com.bank.transfer.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;

/**
 // Бизнес-метрики transfer-service.
 //
 // Ключевые алерты:
 //   CRITICAL: transfer_saved_total rate == 0 за 5 минут (сервис не обрабатывает переводы)
 //   CRITICAL: transfer_idempotent_skipped_total / transfer_saved_total > 20% (повторные доставки)
 //   WARNING:  transfer_amount_summary p99 > 500 000 (нетипично крупные переводы)
 */
@Component
@Getter
public class TransferMetrics {

    /** Сохранённые переводы по типу и результату */
    private final Counter accountTransferSaved;
    private final Counter cardTransferSaved;
    private final Counter phoneTransferSaved;

    /** Переводы, пропущенные idempotency guard */
    private final Counter accountTransferSkipped;
    private final Counter cardTransferSkipped;
    private final Counter phoneTransferSkipped;

    /** Ошибки сохранения переводов */
    private final Counter transferFailed;

 /**
     * Распределение сумм переводов — позволяет видеть p50/p95/p99 в Grafana.
     * Нетипично большие суммы → алерт для антифрода.
 */
    private final DistributionSummary transferAmountSummary;

    /** Gauge: total stored transfers per type */
    public TransferMetrics(MeterRegistry registry,
                           AccountTransferRepository accountRepo,
                           CardTransferRepository cardRepo,
                           PhoneTransferRepository phoneRepo) {

        this.accountTransferSaved = Counter.builder("transfer_saved_total")
                .tag("type", "account")
                .description("Account transfers successfully saved to DB")
                .register(registry);

        this.cardTransferSaved = Counter.builder("transfer_saved_total")
                .tag("type", "card")
                .description("Card transfers successfully saved to DB")
                .register(registry);

        this.phoneTransferSaved = Counter.builder("transfer_saved_total")
                .tag("type", "phone")
                .description("Phone transfers successfully saved to DB")
                .register(registry);

        this.accountTransferSkipped = Counter.builder("transfer_idempotent_skipped_total")
                .tag("type", "account")
                .description("Duplicate account transfers blocked by idempotency guard")
                .register(registry);

        this.cardTransferSkipped = Counter.builder("transfer_idempotent_skipped_total")
                .tag("type", "card")
                .description("Duplicate card transfers blocked by idempotency guard")
                .register(registry);

        this.phoneTransferSkipped = Counter.builder("transfer_idempotent_skipped_total")
                .tag("type", "phone")
                .description("Duplicate phone transfers blocked by idempotency guard")
                .register(registry);

        this.transferFailed = Counter.builder("transfer_failed_total")
                .description("Transfer save failures (any type)")
                .register(registry);

        this.transferAmountSummary = DistributionSummary.builder("transfer_amount_summary")
                .description("Distribution of transfer amounts (in base currency units)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Gauges: актуальное количество переводов в БД
        Gauge.builder("transfer_total_count", accountRepo, repo -> (double) repo.count())
                .tag("type", "account")
                .description("Total account transfers in DB")
                .register(registry);

        Gauge.builder("transfer_total_count", cardRepo, repo -> (double) repo.count())
                .tag("type", "card")
                .description("Total card transfers in DB")
                .register(registry);

        Gauge.builder("transfer_total_count", phoneRepo, repo -> (double) repo.count())
                .tag("type", "phone")
                .description("Total phone transfers in DB")
                .register(registry);
    }
}
