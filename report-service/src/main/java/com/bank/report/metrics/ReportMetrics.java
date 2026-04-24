package com.bank.report.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Бизнес-метрики report-service.
 */
 /**
 * Алерты:
 *   WARNING: rate(report_payments_stored_total[5m]) == 0 при наличии платежей
 *   WARNING: rate(report_transfers_stored_total[5m]) == 0 при наличии переводов
 *   WARNING: report_csv_generation_seconds p99 > 10s — медленная генерация
 */
@ApplicationScoped
public class ReportMetrics {

    @Inject MeterRegistry registry;

    private Counter paymentsStored;
    private Counter transfersStored;
    private Counter kafkaDuplicatesSkipped;
    private Counter csvReportsGenerated;
    private Counter csvReportsFailed;
    private Timer   csvGenerationTimer;

    @PostConstruct
    void init() {
        paymentsStored = Counter.builder("report_payments_stored_total")
                .description("Payment events stored in report DB").register(registry);
        transfersStored = Counter.builder("report_transfers_stored_total")
                .description("Transfer events stored in report DB").register(registry);
        kafkaDuplicatesSkipped = Counter.builder("report_kafka_duplicate_total")
                .description("Duplicate Kafka events skipped").register(registry);
        csvReportsGenerated = Counter.builder("report_csv_generated_total")
                .description("CSV reports successfully generated").register(registry);
        csvReportsFailed = Counter.builder("report_csv_failed_total")
                .description("CSV report generation failures").register(registry);
        csvGenerationTimer = Timer.builder("report_csv_generation_seconds")
                .description("Time to generate a CSV report")
                .publishPercentiles(0.50, 0.95, 0.99).register(registry);
    }

    public Counter getPaymentsStored()         { return paymentsStored; }
    public Counter getTransfersStored()        { return transfersStored; }
    public Counter getKafkaDuplicatesSkipped() { return kafkaDuplicatesSkipped; }
    public Counter getCsvReportsGenerated()    { return csvReportsGenerated; }
    public Counter getCsvReportsFailed()       { return csvReportsFailed; }
    public Timer   getCsvGenerationTimer()     { return csvGenerationTimer; }
}
