package com.bank.report.consumer;

import com.bank.report.entity.TransferReport;
import com.bank.report.event.TransferNotificationEvent;
import com.bank.report.metrics.ReportMetrics;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.LocalDate;

/**
 * Kafka-консьюмер событий переводов.
 */
 /**
 * Слушает transfer.notification — события от transfer-service
 * о создании, завершении, блокировке и отмене переводов.
 */
 /**
 * Стратегия для UPSERT:
 *   CREATED → insert новой записи (если нет дубля).
 *   COMPLETED / BLOCKED / CANCELLED → обновить статус существующей.
 *   Если запись не найдена при обновлении — создать (компенсация out-of-order).
 */
@ApplicationScoped
public class TransferReportConsumer {

    private static final Logger LOG = Logger.getLogger(TransferReportConsumer.class);

    @Inject ReportMetrics metrics;

    @Incoming("transfer-notification")
    @Blocking
    @Transactional
    public void handleTransferEvent(TransferNotificationEvent event) {
        if (event == null || event.transferId == null) {
            LOG.warn("Received null or incomplete TransferNotificationEvent, skipping");
            return;
        }

        TransferReport existing = TransferReport
                .find("transferId", event.transferId).firstResult();

        if (existing != null) {
            // Идемпотентное обновление статуса
            if (event.status.equals(existing.getStatus())) {
                LOG.debugf("TransferReport transferId=%d already has status=%s, skipping",
                           event.transferId, event.status);
                metrics.getKafkaDuplicatesSkipped().increment();
                return;
            }
            existing.setStatus(event.status);
            existing.setReason(event.reason);
            LOG.infof("TransferReport updated: transferId=%d → status=%s",
                      event.transferId, event.status);
        } else {
            // Новая запись (CREATED или out-of-order финальный статус)
            TransferReport report = new TransferReport();
            report.setTransferId(event.transferId);
            report.setClientId(event.clientId);
            report.setTransferType(event.transferType);
            report.setStatus(event.status);
            report.setAmount(event.amount);
            report.setCurrency(event.currency);
            report.setRecipientDisplay(event.recipientDisplay);
            report.setReason(event.reason);
            report.setOccurredAt(event.occurredAt != null
                    ? event.occurredAt
                    : java.time.LocalDateTime.now());
            report.setReportDate(event.occurredAt != null
                    ? event.occurredAt.toLocalDate()
                    : LocalDate.now());
            report.persist();
            LOG.infof("TransferReport stored: transferId=%d clientId=%d status=%s",
                      event.transferId, event.clientId, event.status);
        }

        metrics.getTransfersStored().increment();
    }
}
