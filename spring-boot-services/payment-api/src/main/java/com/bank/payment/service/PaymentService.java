package com.bank.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bank.payment.dto.CreatePaymentRequest;
import com.bank.payment.dto.PaymentResponse;
import com.bank.payment.entity.Payment;
import com.bank.payment.entity.PaymentStatus;
import com.bank.payment.event.PaymentCreatedEvent;
import com.bank.payment.exception.PaymentNotFoundException;
import com.bank.payment.antifraud.AntifraudKafkaProducer;
import com.bank.payment.antifraud.AntifraudRequestEvent;
import com.bank.payment.metrics.PaymentMetrics;
import com.bank.payment.outbox.OutboxEvent;
import com.bank.payment.outbox.OutboxRepository;
import com.bank.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository  outboxRepository;
    private final ObjectMapper      objectMapper;
    private final PaymentMetrics         metrics;
    private final AntifraudKafkaProducer antifraudProducer;

    @Transactional
    public PaymentResponse createPayment(String clientId,
                                         String idempotencyKey,
                                         CreatePaymentRequest request) {

        // recordCallable throws checked Exception — wrapping so caller sees only unchecked
        try {
            return metrics.getPaymentProcessingTimer().recordCallable(() -> {
            // ── Idempotency fast path ────────────────────────────────────────
            Payment existing = paymentRepository
                    .findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                metrics.getIdempotentHits().increment();
                return mapToResponse(existing);
            }

            // ── Validation ───────────────────────────────────────────────────
            try {
                validateAmount(request.getAmount());
            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("exceeds maximum")) {
                    metrics.getAmountLimitExceeded().increment();
                }
                metrics.getPaymentFailed().increment();
                throw e;
            }

            Payment payment = new Payment();
            payment.setClientId(clientId);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setRecipientAccount(request.getRecipientAccount());
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency());
            payment.setDescription(request.getDescription());
            payment.setStatus(PaymentStatus.CREATED);
            payment.setCreatedAt(LocalDateTime.now());

            try {
                Payment saved = paymentRepository.save(payment);
                savePaymentCreatedToOutbox(saved);
                enqueueAntifraudCheck(saved, request);

                // ── Business metrics: success ────────────────────────────────
                metrics.recordPaymentCreated(request.getCurrency());

                return mapToResponse(saved);

            } catch (DataIntegrityViolationException e) {
                // Race condition: параллельный запрос успел первым
                metrics.getRaceConditionHits().increment();
                return paymentRepository
                        .findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                        .map(this::mapToResponse)
                        .orElseThrow(() -> new RuntimeException(
                                "Unexpected state after constraint violation", e));
            } catch (Exception e) {
                metrics.getPaymentFailed().increment();
                throw e;
            }
        });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    public PaymentResponse getPaymentStatus(String clientId, Long paymentId) {
        return mapToResponse(
                paymentRepository.findByIdAndClientId(paymentId, clientId)
                        .orElseThrow(() -> new PaymentNotFoundException(paymentId)));
    }

    public PaymentResponse getPaymentByKey(String clientId, String idempotencyKey) {
        return mapToResponse(
                paymentRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                        .orElseThrow(() -> new PaymentNotFoundException(idempotencyKey)));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void savePaymentCreatedToOutbox(Payment payment) {
        // Последние 4 цифры счёта-получателя используются как cardLastFour
        // для отображения в push-уведомлении формата «KARTA #4321».
        // При интеграции с card-service здесь можно подставить реальный номер карты.
        String cardLastFour = extractLastFour(payment.getRecipientAccount());
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                payment.getId(), payment.getClientId(), payment.getRecipientAccount(),
                payment.getAmount(), payment.getCurrency(),
                payment.getStatus().name(), payment.getCreatedAt(), cardLastFour);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize PaymentCreatedEvent", e);
        }
        outboxRepository.save(OutboxEvent.builder()
                .topic("payment.created")
                .partitionKey(payment.getId().toString())
                .eventType("PaymentCreated")
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());
    }

 /**
     * Ставит запрос на антифрод-проверку в outbox (в той же транзакции).
     * transferType=ACCOUNT — payment-api работает только со счётами.
 */
    private void enqueueAntifraudCheck(Payment payment, CreatePaymentRequest request) {
        AntifraudRequestEvent antifraudRequest = AntifraudRequestEvent.builder()
                .paymentId(payment.getId())
                .clientId(payment.getClientId())
                .recipientAccount(payment.getRecipientAccount())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transferType("ACCOUNT")
                .build();
        antifraudProducer.enqueueAntifraudCheck(antifraudRequest);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (amount.compareTo(new BigDecimal("10000000.00")) > 0)
            throw new IllegalArgumentException("Amount exceeds maximum limit");
    }

    private PaymentResponse mapToResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getClientId(), p.getRecipientAccount(),
                p.getAmount(), p.getCurrency(), p.getStatus().name(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

 /**
     * Извлекает последние 4 цифры из строки счёта/номера карты.
     * Возвращает null если строка короче 4 символов.
 */
    private String extractLastFour(String account) {
        if (account == null || account.length() < 4) return null;
        return account.substring(account.length() - 4);
    }
}
