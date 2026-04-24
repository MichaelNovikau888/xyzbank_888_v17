package com.bank.transfer.antifraud;

import com.bank.transfer.enums.TransferStatus;
import com.bank.transfer.notification.TransferNotificationOutboxHelper;
import com.bank.transfer.outbox.HistoryOutboxHelper;
import com.bank.transfer.repository.AccountTransferRepository;
import com.bank.transfer.repository.CardTransferRepository;
import com.bank.transfer.repository.PhoneTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka-консьюмер ответов антифрода на проверки переводов.
 */
 /**
 * Слушает: transfer.antifraud.response
 */
 /**
 * При получении ответа (в одной транзакции):
 *   1. Обновляет status в entity (AccountTransfer / CardTransfer / PhoneTransfer)
 *   2. Шлёт уведомление в transfer.notification → notification-service
 *   3. Шлёт событие в transfer.events → history-service
 */
 /**
 * ALLOW  → статус COMPLETED: enqueueFinal + TransferCompleted
 * REVIEW → статус REVIEW:    enqueueReview + TransferReview (промежуточный, не финальный)
 * BLOCK  → статус BLOCKED:   enqueueFinal + TransferBlocked
 */
 /**
 * Идемпотентность: если previousStatus != CREATED → пропускаем.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AntifraudResponseConsumer {

    private final HistoryOutboxHelper              historyOutboxHelper;
    private final TransferNotificationOutboxHelper notificationOutboxHelper;
    private final AccountTransferRepository        accountTransferRepository;
    private final CardTransferRepository           cardTransferRepository;
    private final PhoneTransferRepository          phoneTransferRepository;

    @KafkaListener(
            topics = "transfer.antifraud.response",
            groupId = "transfer-antifraud-response-group",
            containerFactory = "transferAntifraudResponseListenerContainerFactory")
    @Transactional
    public void handleAntifraudResponse(@Payload AntifraudResponseEvent response) {

        log.info("Antifraud response for transfer: transferId={} type={} decision={} riskScore={}",
                response.getTransferId(), response.getTransferType(),
                response.getDecision(), response.getRiskScore());

        String decision      = response.getDecision();
        String transferType  = response.getTransferType() != null ? response.getTransferType() : "ACCOUNT";
        Long   transferId    = response.getTransferId();

        // Определяем итоговый статус перевода
        TransferStatus newStatus = switch (decision) {
            case "BLOCK"  -> TransferStatus.BLOCKED;
            case "REVIEW" -> TransferStatus.REVIEW;
            default       -> TransferStatus.COMPLETED;   // ALLOW и любой неизвестный
        };

        // Загружаем данные нужного entity и обновляем статус
        TransferDetails details = loadAndUpdateStatus(transferId, transferType, newStatus);

        if (details == null) {
            log.error("Transfer not found or type unknown: transferId={} type={}", transferId, transferType);
            return;
        }

        // Idempotency: если статус уже финальный — пропускаем
        if (details.previousStatus() != TransferStatus.CREATED) {
            log.warn("Transfer transferId={} already in status={}, skipping",
                     transferId, details.previousStatus());
            return;
        }

        String reason = response.getReason();

        if (newStatus == TransferStatus.REVIEW) {
            // REVIEW — промежуточный: только push, без email и записи в БД
            notificationOutboxHelper.enqueueReview(
                    transferId, details.clientId(), transferType,
                    details.amount(), "RUB");

            historyOutboxHelper.enqueueTransferEvent(
                    transferId.toString(),
                    "TransferReview",
                    buildHistoryPayload(transferId, transferType, decision, newStatus, reason,
                                       response.getRiskScore()));
        } else {
            // COMPLETED или BLOCKED — финальный статус
            notificationOutboxHelper.enqueueFinal(
                    transferId, details.clientId(), transferType, newStatus,
                    details.amount(), "RUB",
                    details.recipientDisplay(), details.purpose(),
                    reason);

            historyOutboxHelper.enqueueTransferEvent(
                    transferId.toString(),
                    newStatus == TransferStatus.BLOCKED ? "TransferBlocked" : "TransferCompleted",
                    buildHistoryPayload(transferId, transferType, decision, newStatus, reason,
                                       response.getRiskScore()));
        }

        log.info("Transfer {} → {}: transferId={}", decision, newStatus, transferId);
    }

    private Map<String, Object> buildHistoryPayload(Long transferId, String transferType,
                                                     String decision, TransferStatus status,
                                                     String reason, int riskScore) {
        return Map.of(
                "transferId",   transferId,
                "transferType", transferType,
                "decision",     decision,
                "status",       status.name(),
                "reason",       reason != null ? reason : "",
                "riskScore",    riskScore,
                "occurredAt",   LocalDateTime.now().toString()
        );
    }

    // ── Private ───────────────────────────────────────────────────────────────

 /**
     * Загружает entity по transferId и transferType, обновляет статус.
     * Возвращает детали для notification и history, или null если не найдено.
 */
    private TransferDetails loadAndUpdateStatus(Long transferId, String transferType,
                                                 TransferStatus newStatus) {
        return switch (transferType) {
            case "CARD" -> cardTransferRepository.findById(transferId).map(e -> {
                TransferStatus prev = e.getStatus();
                e.setStatus(newStatus);
                cardTransferRepository.save(e);
                String card = e.getCardNumber() != null
                        ? "**** **** **** " + e.getCardNumber().substring(
                              Math.max(0, e.getCardNumber().length() - 4))
                        : "****";
                return new TransferDetails(prev, e.getClientId(), e.getAmount(),
                                           card, e.getPurpose());
            }).orElse(null);

            case "PHONE" -> phoneTransferRepository.findById(transferId).map(e -> {
                TransferStatus prev = e.getStatus();
                e.setStatus(newStatus);
                phoneTransferRepository.save(e);
                String phone = e.getPhoneNumber() != null
                        ? e.getPhoneNumber()
                        : "****";
                return new TransferDetails(prev, e.getClientId(), e.getAmount(),
                                           phone, e.getPurpose());
            }).orElse(null);

            default -> accountTransferRepository.findById(transferId).map(e -> {
                TransferStatus prev = e.getStatus();
                e.setStatus(newStatus);
                accountTransferRepository.save(e);
                return new TransferDetails(prev, e.getClientId(), e.getAmount(),
                                           e.getAccountNumber(), e.getPurpose());
            }).orElse(null);
        };
    }

    /** Value object: данные перевода нужные для уведомления. */
    private record TransferDetails(
            TransferStatus previousStatus,
            String         clientId,
            BigDecimal     amount,
            String         recipientDisplay,
            String         purpose
    ) {}
}
