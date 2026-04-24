package com.bank.transfer.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ставит события переводов в outbox для топика transfer.events.
 */
 /**
 * History-service слушает transfer.events → handleTransferEvent.
 * Outbox гарантирует доставку: событие сохраняется в той же транзакции,
 * OutboxRelayScheduler публикует его в Kafka когда брокер доступен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryOutboxHelper {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper     objectMapper;

 /**
     * @param partitionKey идентификатор перевода (id счёта/карты/телефона)
     * @param eventType    тип события: AccountTransferCreated / CardTransferCreated / PhoneTransferCreated
     * @param payload      DTO перевода
 */
    public void enqueueTransferEvent(String partitionKey, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize history payload for eventType=" + eventType, e);
        }

        outboxRepository.save(OutboxEvent.builder()
                .topic("transfer.events")
                .partitionKey(partitionKey)
                .eventType(eventType)
                .payload(json)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());

        log.debug("HistoryOutbox enqueued: type={} key={}", eventType, partitionKey);
    }
}
