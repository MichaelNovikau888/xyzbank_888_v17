package com.bank.transfer.notification;

import com.bank.transfer.enums.TransferStatus;
import com.bank.transfer.outbox.OutboxEvent;
import com.bank.transfer.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ставит уведомления о переводах в outbox → transfer.notification.
 */
 /**
 * Топик: transfer.notification (слушает notification-service)
 * Гарантия: outbox в одной транзакции с изменением статуса перевода.
 */
 /**
 * Методы:
 *   enqueueCreated()   — при сохранении нового перевода
 *   enqueueReview()    — при получении REVIEW от antifraud (ручная проверка)
 *   enqueueFinal()     — при получении ответа от antifraud (COMPLETED/BLOCKED)
 *   enqueueCancelled() — при отмене перевода клиентом
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferNotificationOutboxHelper {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper     objectMapper;

    // ── Public API ────────────────────────────────────────────────────────────

 /**
     * Уведомление «Перевод создан».
     * Push: «Перевод №X на сумму Y RUB создан»
     * Email: не отправляется (только push при создании)
 */
    public void enqueueCreated(Long transferId, String clientId,
                                String transferType, BigDecimal amount, String currency,
                                String recipientDisplay, String purpose) {
        enqueue(TransferNotificationEvent.builder()
                .transferId(transferId)
                .clientId(clientId)
                .transferType(transferType)
                .status(TransferStatus.CREATED)
                .amount(amount)
                .currency(currency)
                .recipientDisplay(recipientDisplay)
                .purpose(purpose)
                .occurredAt(LocalDateTime.now())
                .build());
    }

 /**
     * Уведомление «Перевод на ручной проверке» (REVIEW от антифрода).
     * Push: «Перевод №X на сумму Y RUB отправлен на проверку — ожидайте до 24 часов»
     * Email: не отправляется (промежуточный, не финальный статус)
     * БД: не пишется (только финальные статусы сохраняются)
 */
    public void enqueueReview(Long transferId, String clientId,
                               String transferType, BigDecimal amount, String currency) {
        enqueue(TransferNotificationEvent.builder()
                .transferId(transferId)
                .clientId(clientId)
                .transferType(transferType)
                .status(TransferStatus.REVIEW)
                .amount(amount)
                .currency(currency)
                .reason(null)
                .occurredAt(LocalDateTime.now())
                .build());
    }

 /**
     * Уведомление о финальном статусе: COMPLETED, BLOCKED, CANCELLED.
     * Push: краткое по статусу
     * Email: подробное (сумма, получатель, дата, причина)
 */
    public void enqueueFinal(Long transferId, String clientId,
                              String transferType, TransferStatus status,
                              BigDecimal amount, String currency,
                              String recipientDisplay, String purpose,
                              String reason) {
        enqueue(TransferNotificationEvent.builder()
                .transferId(transferId)
                .clientId(clientId)
                .transferType(transferType)
                .status(status)
                .amount(amount)
                .currency(currency)
                .recipientDisplay(recipientDisplay)
                .purpose(purpose)
                .reason(reason)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void enqueue(TransferNotificationEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize TransferNotificationEvent for transferId="
                    + event.getTransferId(), e);
        }

        outboxRepository.save(OutboxEvent.builder()
                .topic("transfer.notification")
                .partitionKey(event.getTransferId().toString())
                .eventType("Transfer" + capitalise(event.getStatus().name()))
                .payload(json)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());

        log.debug("TransferNotification enqueued: transferId={} status={}",
                  event.getTransferId(), event.getStatus());
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
