package com.bank.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;
import com.bank.payment.repository.PaymentRepository;

import java.util.concurrent.atomic.AtomicLong;

/**
 // Бизнес-метрики payment-api для Prometheus / Grafana.
 //
 // Метрики делятся на три уровня:
 //
 // 1. ОПЕРАЦИОННЫЕ (мониторинг здоровья прямо сейчас):
 //    - payment_created_total        — сколько платежей создано
 //    - payment_failed_total         — сколько платежей упало с ошибкой
 //    - payment_idempotent_hits_total — сколько повторных запросов отбито
 //    - payment_race_condition_total  — сколько race condition словили (100k RPS индикатор)
 //
 // 2. БИЗНЕС-ОБЪЁМ (финансовый мониторинг):
 //    - payment_amount_rub_total      — сумма в RUB через DistributionSummary
 //    - payment_amount_usd_total      — сумма в USD
 //    - payment_pending_count         — Gauge: сколько платежей в статусе CREATED прямо сейчас
 //
 // 3. ПРОИЗВОДИТЕЛЬНОСТЬ:
 //    - payment_processing_duration   — Timer: время от получения запроса до сохранения
 //
 // Алерты (настроить в Alertmanager):
 //   CRITICAL: payment_failed_total rate > 5% за 5 минут
 //   WARNING:  payment_pending_count > 1000 (зависшие платежи)
 //   INFO:     payment_race_condition_total rate > 100/min (нагрузка близка к пределу)
 */
@Component
@Getter
public class PaymentMetrics {

    // ── Операционные счётчики ────────────────────────────────────────────────

    /** Успешно созданные платежи, разбитые по валюте */
    private final Counter paymentCreatedRub;
    private final Counter paymentCreatedUsd;
    private final Counter paymentCreatedOther;

    /** Платежи с ошибкой (валидация, БД, etc.) */
    private final Counter paymentFailed;

    /** Повторные запросы с тем же idempotency-key (нормально — at-least-once) */
    private final Counter idempotentHits;

 /**
     * Race condition: два запроса прилетели одновременно, второй поймал
     * DataIntegrityViolationException и был gracefully resolved.
     * Если этот счётчик растёт — значит нагрузка действительно 100k+ RPS.
 */
    private final Counter raceConditionHits;

    /** Превышение лимита суммы (> 10 000 000) */
    private final Counter amountLimitExceeded;

    // ── Бизнес-объём ─────────────────────────────────────────────────────────

    /** Таймер обработки createPayment() — от входа до выхода */
    private final Timer paymentProcessingTimer;

    /** Gauge: платежи в статусе CREATED (не завершённые) — мониторинг зависших */
    private final AtomicLong pendingPaymentsCount = new AtomicLong(0);

    public PaymentMetrics(MeterRegistry registry, PaymentRepository paymentRepository) {

        // Счётчики создания по валюте
        this.paymentCreatedRub = Counter.builder("payment_created_total")
                .description("Total number of payments successfully created")
                .tag("currency", "RUB")
                .register(registry);

        this.paymentCreatedUsd = Counter.builder("payment_created_total")
                .description("Total number of payments successfully created")
                .tag("currency", "USD")
                .register(registry);

        this.paymentCreatedOther = Counter.builder("payment_created_total")
                .description("Total number of payments successfully created")
                .tag("currency", "OTHER")
                .register(registry);

        this.paymentFailed = Counter.builder("payment_failed_total")
                .description("Total number of payment creation failures")
                .register(registry);

        this.idempotentHits = Counter.builder("payment_idempotent_hits_total")
                .description("Repeated requests blocked by idempotency key (at-least-once delivery)")
                .register(registry);

        this.raceConditionHits = Counter.builder("payment_race_condition_total")
                .description("Race conditions resolved via DataIntegrityViolationException (high load indicator)")
                .register(registry);

        this.amountLimitExceeded = Counter.builder("payment_amount_limit_exceeded_total")
                .description("Payment attempts exceeding the maximum amount limit")
                .register(registry);

        this.paymentProcessingTimer = Timer.builder("payment_processing_duration_seconds")
                .description("Time to process a createPayment() request end-to-end")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Gauge читает актуальное значение из БД при каждом скрейпе Prometheus
        Gauge.builder("payment_pending_count", paymentRepository,
                        repo -> repo.countByStatus(
                                com.bank.payment.entity.PaymentStatus.CREATED))
                .description("Number of payments in CREATED status (not yet processed)")
                .register(registry);
    }

    /** Инкрементируем счётчик создания с разбивкой по валюте */
    public void recordPaymentCreated(String currency) {
        switch (currency != null ? currency.toUpperCase() : "") {
            case "RUB" -> paymentCreatedRub.increment();
            case "USD" -> paymentCreatedUsd.increment();
            default    -> paymentCreatedOther.increment();
        }
    }
}
