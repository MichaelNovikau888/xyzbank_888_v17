package com.bank.report.consumer;

import com.bank.report.entity.PaymentReport;
import com.bank.report.event.PaymentCreatedEvent;
import com.bank.report.event.PaymentStatusChangedEvent;
import com.bank.report.metrics.ReportMetrics;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Kafka-консьюмер платёжных событий.
 */
 /**
 * Вынесен из ReportService в отдельный пакет consumer —
 * нарушение SRP исправлено: сервисный слой содержит только бизнес-логику.
 */
 /**
 * Exactly-once delivery:
 *   Kafka consumer настроен на isolation.level=read_committed и
 *   enable.idempotence=true на продюсере (см. application.properties).
 *   Дополнительно: уникальный индекс на payment_id гарантирует,
 *   что дубль из at-least-once не запишется повторно.
 */
@ApplicationScoped
public class PaymentReportConsumer {

    private static final Logger LOG = Logger.getLogger(PaymentReportConsumer.class);

    @Inject ReportMetrics metrics;

    @Incoming("payment-created")
    @Blocking
    @Transactional
    public void handlePaymentCreated(PaymentCreatedEvent event) {
        if (event == null || event.paymentId == null) {
            LOG.warn("Received null or incomplete PaymentCreatedEvent, skipping");
            return;
        }

        // Идемпотентность: уникальный индекс на payment_id —
        // повторная запись упадёт на constraint, а не создаст дубль
        if (PaymentReport.find("paymentId", event.paymentId).firstResult() != null) {
            LOG.warnf("Duplicate payment.created for paymentId=%d, skipping", event.paymentId);
            metrics.getKafkaDuplicatesSkipped().increment();
            return;
        }

        PaymentReport report = new PaymentReport();
        report.setPaymentId(event.paymentId);
        report.setClientId(event.clientId);
        report.setAmount(event.amount);
        report.setCurrency(event.currency);
        report.setStatus(event.status);
        report.setRecipientAccount(event.recipientAccount);
        report.setCreatedAt(event.createdAt);
        report.setReportDate(event.createdAt != null
                ? event.createdAt.toLocalDate()
                : java.time.LocalDate.now());
        report.persist();

        metrics.getPaymentsStored().increment();
        LOG.infof("PaymentReport stored: paymentId=%d clientId=%d status=%s",
                  event.paymentId, event.clientId, event.status);
    }

    @Incoming("payment-status-changed")
    @Blocking
    @Transactional
    public void handlePaymentStatusChanged(PaymentStatusChangedEvent event) {
        if (event == null || event.paymentId == null) {
            LOG.warn("Received null or incomplete PaymentStatusChangedEvent, skipping");
            return;
        }

        PaymentReport report = PaymentReport
                .find("paymentId", event.paymentId).firstResult();

        if (report == null) {
            LOG.warnf("No PaymentReport found for paymentId=%d, skipping status update",
                      event.paymentId);
            return;
        }

        // Idempotent update: если статус уже такой — пропускаем
        if (event.newStatus.equals(report.getStatus())) {
            LOG.debugf("PaymentReport paymentId=%d already has status=%s, skipping",
                       event.paymentId, event.newStatus);
            metrics.getKafkaDuplicatesSkipped().increment();
            return;
        }

        report.setStatus(event.newStatus);
        // Panache managed entity — persist() не нужен в @Transactional
        LOG.infof("PaymentReport updated: paymentId=%d %s → %s",
                  event.paymentId, event.oldStatus, event.newStatus);
        metrics.getPaymentsStored().increment();
    }
}
