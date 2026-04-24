package com.bank.authorization.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                stringKafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()).get();
                event.setSentAt(LocalDateTime.now());
                log.info("Outbox: sent id={} type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                log.error("Outbox: failed id={} attempt={} err={}", event.getId(), event.getAttempts(), e.getMessage());
            }
            outboxRepository.save(event);
        }
    }
}
