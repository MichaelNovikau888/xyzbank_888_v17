package com.bank.payment.antifraud;

import com.bank.payment.entity.Payment;
import com.bank.payment.entity.PaymentStatus;
import com.bank.payment.history.HistoryOutboxHelper;
import com.bank.payment.repository.PaymentRepository;
import com.bank.payment.service.PaymentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka-консьюмер ответов антифрода.
 */
 /**
 * Слушает: payment.antifraud.response
 */
 /**
 * По решению antifraud:
 *   ALLOW  → статус PROCESSING + событие в account.events (history)
 *   REVIEW → статус PROCESSING + событие в account.events с меткой ручной проверки
 *   BLOCK  → статус FAILED    + событие в account.events (история блокировки)
 */
 /**
 * Обновление статуса и запись в history outbox происходят в одной транзакции —
 * либо оба изменения применяются, либо ни одно.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AntifraudResponseConsumer {

    private final PaymentRepository    paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final HistoryOutboxHelper  historyOutboxHelper;

    @KafkaListener(
            topics = "payment.antifraud.response",
            groupId = "payment-antifraud-response-group",
            containerFactory = "antifraudResponseListenerContainerFactory")
    @Transactional
    public void handleAntifraudResponse(@Payload AntifraudResponseEvent response) {

        log.info("Antifraud response: paymentId={} decision={} riskScore={}",
                response.getPaymentId(), response.getDecision(), response.getRiskScore());

        Payment payment = paymentRepository.findById(response.getPaymentId()).orElse(null);
        if (payment == null) {
            log.error("Payment not found for antifraud response: paymentId={}", response.getPaymentId());
            return;
        }

        // Идемпотентность: уже обработан
        if (payment.getStatus() != PaymentStatus.CREATED) {
            log.warn("Payment paymentId={} already in status={}, skipping",
                    response.getPaymentId(), payment.getStatus());
            return;
        }

        String decision = response.getDecision();
        String reason   = response.getReason();

        if ("BLOCK".equals(decision)) {
            paymentStatusService.updatePaymentStatus(
                    response.getPaymentId(), PaymentStatus.FAILED,
                    "Blocked by antifraud: " + reason);
            enqueueHistory(payment, PaymentStatus.FAILED, decision, reason, response.getRiskScore());
            log.warn("Payment BLOCKED: paymentId={} reason={}", response.getPaymentId(), reason);

        } else if ("REVIEW".equals(decision)) {
            paymentStatusService.updatePaymentStatus(
                    response.getPaymentId(), PaymentStatus.PROCESSING,
                    "Under manual review (riskScore=" + response.getRiskScore() + "): " + reason);
            enqueueHistory(payment, PaymentStatus.PROCESSING, decision, reason, response.getRiskScore());
            log.info("Payment sent to REVIEW: paymentId={} riskScore={}", response.getPaymentId(), response.getRiskScore());

        } else {
            // ALLOW
            paymentStatusService.updatePaymentStatus(
                    response.getPaymentId(), PaymentStatus.PROCESSING,
                    "Approved by antifraud (riskScore=" + response.getRiskScore() + ")");
            enqueueHistory(payment, PaymentStatus.PROCESSING, decision, reason, response.getRiskScore());
            log.info("Payment ALLOWED: paymentId={}", response.getPaymentId());
        }
    }

 /**
     * Помещает событие статуса платежа в outbox → account.events → history-service.
     * Вызывается внутри той же транзакции что и updatePaymentStatus.
 */
    private void enqueueHistory(Payment payment, PaymentStatus newStatus,
                                 String decision, String reason, int riskScore) {
        Map<String, Object> event = Map.of(
                "paymentId",  payment.getId(),
                "clientId",   payment.getClientId(),
                "amount",     payment.getAmount(),
                "currency",   payment.getCurrency(),
                "status",     newStatus.name(),
                "decision",   decision,
                "reason",     reason,
                "riskScore",  riskScore,
                "occurredAt", LocalDateTime.now().toString()
        );
        historyOutboxHelper.enqueueAccountEvent(
                payment.getId().toString(),
                "PaymentStatusChanged",
                event);
    }
}
