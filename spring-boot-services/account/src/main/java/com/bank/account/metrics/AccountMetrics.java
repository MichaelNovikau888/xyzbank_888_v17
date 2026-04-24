package com.bank.account.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;
import com.bank.account.repository.AccountRepository;
import com.bank.account.repository.CreditCardRepository;

/**
 * Бизнес-метрики account-service.
 */
 /**
 * Ключевые алерты:
 *   CRITICAL: account_created_total rate == 0 за 15 минут (регистрация сломана)
 *   WARNING:  account_idempotent_skipped_total / account_created_total > 10%
 *             (много повторных Kafka-команд — проблема с producer)
 *   WARNING:  credit_card_blocked_total rate > 20/min (массовые блокировки)
 */
@Component
@Getter
public class AccountMetrics {

    private final Counter accountsCreated;
    private final Counter accountsUpdated;
    private final Counter accountsDeleted;
    private final Counter accountIdempotentSkipped;

    private final Counter creditCardsIssued;
    private final Counter creditCardsBlocked;
    private final Counter creditCardLimitsUpdated;

    public AccountMetrics(MeterRegistry registry,
                          AccountRepository accountRepository,
                          CreditCardRepository creditCardRepository) {

        this.accountsCreated = Counter.builder("account_created_total")
                .description("Bank accounts successfully created").register(registry);
        this.accountsUpdated = Counter.builder("account_updated_total")
                .description("Bank accounts successfully updated").register(registry);
        this.accountsDeleted = Counter.builder("account_deleted_total")
                .description("Bank accounts deleted").register(registry);
        this.accountIdempotentSkipped = Counter.builder("account_idempotent_skipped_total")
                .description("Duplicate Kafka commands skipped by idempotency guard").register(registry);

        this.creditCardsIssued = Counter.builder("credit_card_issued_total")
                .description("Credit cards issued").register(registry);
        this.creditCardsBlocked = Counter.builder("credit_card_blocked_total")
                .description("Credit cards blocked").register(registry);
        this.creditCardLimitsUpdated = Counter.builder("credit_card_limit_updated_total")
                .description("Credit card limits updated").register(registry);

        // Gauge: текущее число счетов в системе
        Gauge.builder("account_total_count", accountRepository,
                        repo -> (double) repo.count())
                .description("Total bank accounts currently in DB").register(registry);

        // Gauge: текущее число карт
        Gauge.builder("credit_card_total_count", creditCardRepository,
                        repo -> (double) repo.count())
                .description("Total credit cards currently in DB").register(registry);
    }
}
