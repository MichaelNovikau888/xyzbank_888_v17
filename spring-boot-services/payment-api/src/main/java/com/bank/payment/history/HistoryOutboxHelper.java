package com.bank.payment.history;

import com.bank.payment.outbox.OutboxEvent;
import com.bank.payment.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Ставит платёжные события в outbox для топика account.events.
 */
 /**
 * History-service слушает account.events → handleAccountEvent.
 * Outbox гарантирует at-least-once: событие сохраняется в той же транзакции
 * что и обновление статуса платежа.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryOutboxHelper {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper     objectMapper;

    public void enqueueAccountEvent(String partitionKey, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize history payload for eventType=" + eventType, e);
        }
        outboxRepository.save(OutboxEvent.builder()
                .topic("account.events")
                .partitionKey(partitionKey)
                .eventType(eventType)
                .payload(json)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());
        log.debug("HistoryOutbox enqueued: type={} key={}", eventType, partitionKey);
    }
}
