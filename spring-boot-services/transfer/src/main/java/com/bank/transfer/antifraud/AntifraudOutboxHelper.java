package com.bank.transfer.antifraud;

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
 * Ставит запрос антифрод-проверки перевода в outbox.
 */
 /**
 * Топик: transfer.antifraud.check
 * Outbox гарантирует доставку: запрос сохраняется в той же транзакции,
 * OutboxRelayScheduler публикует его в Kafka когда брокер доступен.
 */
 /**
 * Вызывается из TransferServiceImpl после сохранения перевода в БД.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AntifraudOutboxHelper {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper     objectMapper;

 /**
     * Ставит в outbox событие проверки account-перевода.
 */
 /**
     * @param transferId id сохранённого AccountTransfer
     * @param amount     сумма перевода
     * @param purpose    назначение платежа
 */
    public void enqueueAccountTransferCheck(Long transferId, BigDecimal amount, String purpose) {
        enqueue(transferId, "ACCOUNT", amount, purpose);
    }

 /**
     * Ставит в outbox событие проверки card-перевода.
 */
    public void enqueueCardTransferCheck(Long transferId, BigDecimal amount, String purpose) {
        enqueue(transferId, "CARD", amount, purpose);
    }

 /**
     * Ставит в outbox событие проверки phone-перевода.
 */
    public void enqueuePhoneTransferCheck(Long transferId, BigDecimal amount, String purpose) {
        enqueue(transferId, "PHONE", amount, purpose);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void enqueue(Long transferId, String transferType,
                          BigDecimal amount, String purpose) {
        AntifraudRequestEvent event = AntifraudRequestEvent.builder()
                .transferId(transferId)
                .transferType(transferType)
                .amount(amount)
                .purpose(purpose)
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize AntifraudRequestEvent for transferId=" + transferId, e);
        }

        outboxRepository.save(OutboxEvent.builder()
                .topic("transfer.antifraud.check")
                .partitionKey(transferId.toString())   // partition key = transferId → порядок сообщений
                .eventType("TransferAntifraudCheck")
                .payload(json)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());

        log.debug("AntifraudOutbox enqueued: transferId={} type={} amount={}",
                  transferId, transferType, amount);
    }
}
