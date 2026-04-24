package com.bank.publicinfo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;

/**
 * Бизнес-метрики public-info-service.
/
 * Алерты:
 *   WARNING: rate(publicinfo_kafka_errors_total[5m]) > 0
 *            — ошибки обработки Kafka-команд
 *   INFO:    rate(publicinfo_bank_details_created_total[30m]) == 0
 *            — регистрация банков не идёт
 */
@Getter
@ApplicationScoped
public class PublicInfoMetrics {

    @Inject MeterRegistry registry;

    private Counter bankDetailsCreated;
    private Counter bankDetailsUpdated;
    private Counter bankDetailsDeleted;
    private Counter branchCreated;
    private Counter branchUpdated;
    private Counter branchDeleted;
    private Counter atmCreated;
    private Counter atmUpdated;
    private Counter atmDeleted;
    private Counter licenseCreated;
    private Counter certificateCreated;
    private Counter kafkaErrors;

    @PostConstruct
    void init() {
        bankDetailsCreated  = c("publicinfo_bank_details_created_total",  "BankDetails created via Kafka");
        bankDetailsUpdated  = c("publicinfo_bank_details_updated_total",  "BankDetails updated via Kafka");
        bankDetailsDeleted  = c("publicinfo_bank_details_deleted_total",  "BankDetails deleted via Kafka");
        branchCreated       = c("publicinfo_branch_created_total",        "Branches created via Kafka");
        branchUpdated       = c("publicinfo_branch_updated_total",        "Branches updated via Kafka");
        branchDeleted       = c("publicinfo_branch_deleted_total",        "Branches deleted via Kafka");
        atmCreated          = c("publicinfo_atm_created_total",           "ATMs created via Kafka");
        atmUpdated          = c("publicinfo_atm_updated_total",           "ATMs updated via Kafka");
        atmDeleted          = c("publicinfo_atm_deleted_total",           "ATMs deleted via Kafka");
        licenseCreated      = c("publicinfo_license_created_total",       "Licenses created via Kafka");
        certificateCreated  = c("publicinfo_certificate_created_total",   "Certificates created via Kafka");
        kafkaErrors         = c("publicinfo_kafka_errors_total",          "Kafka command processing errors");
    }

    private Counter c(String name, String desc) {
        return Counter.builder(name).description(desc).register(registry);
    }
}
