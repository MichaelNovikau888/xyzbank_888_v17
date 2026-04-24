package com.bank.payment.antifraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.payment.outbox.OutboxEvent;
import com.bank.payment.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Отправляет запрос на антифрод-проверку через Outbox-паттерн.
 */
 /**
 * Почему через Outbox, а не напрямую через KafkaTemplate:
 * - Запись в outbox происходит в той же транзакции что и создание платежа.
 * - Если Kafka недоступна — платёж всё равно сохранён, OutboxRelayScheduler
 *   отправит запрос когда Kafka восстановится.
 * - Гарантия at-least-once delivery без риска потери сообщения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AntifraudKafkaProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper     objectMapper;

 /**
     * Ставит AntifraudRequestEvent в outbox.
     * Вызывается внутри транзакции createPayment.
 */
    public void enqueueAntifraudCheck(AntifraudRequestEvent request) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AntifraudRequestEvent", e);
        }

        outboxRepository.save(OutboxEvent.builder()
                .topic("payment.antifraud.check")
                .partitionKey(request.getPaymentId().toString())
                .eventType("AntifraudCheck")
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());

        log.debug("AntifraudCheck enqueued to outbox: paymentId={}", request.getPaymentId());
    }
}
