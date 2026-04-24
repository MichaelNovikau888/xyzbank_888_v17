package com.bank.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Relay-планировщик Outbox.
 /
 * Каждые 500ms читает неотправленные события из outbox_events и публикует в Kafka.
 * При успехе помечает событие sent_at = now().
 * При ошибке увеличивает attempts; после 5 попыток событие больше не читается
 * (можно добавить алерт или DLQ-логику).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: found {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                stringKafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.setSentAt(LocalDateTime.now());
                log.info("Outbox: sent event id={} type={} topic={}", event.getId(), event.getEventType(), event.getTopic());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                log.error("Outbox: failed to send event id={} attempt={} error={}",
                        event.getId(), event.getAttempts(), e.getMessage());
            }
            outboxRepository.save(event);
        }
    }
}
