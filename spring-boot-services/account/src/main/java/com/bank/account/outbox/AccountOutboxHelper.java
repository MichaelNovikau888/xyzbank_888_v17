package com.bank.account.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Вспомогательный компонент для записи событий в Outbox.
 * Вызывается из AccountServiceImpl и CreditCardServiceImpl
 * внутри уже открытой @Transactional транзакции.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOutboxHelper {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(String topic, String partitionKey, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload for eventType=" + eventType, e);
        }
        outboxRepository.save(OutboxEvent.builder()
                .topic(topic)
                .partitionKey(partitionKey)
                .eventType(eventType)
                .payload(json)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());
        log.debug("Outbox enqueued: type={} topic={} key={}", eventType, topic, partitionKey);
    }
}
