package com.bank.payment.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bank.payment.entity.Payment;
import com.bank.payment.entity.PaymentStatus;
import com.bank.payment.event.PaymentStatusChangedEvent;
import com.bank.payment.exception.PaymentNotFoundException;
import com.bank.payment.repository.PaymentRepository;

import java.time.LocalDateTime;

/**
 * Сервис для обновления статуса платежа
 * Используется операционистами для проверки подозрительных платежей
 */
@Service
public class PaymentStatusService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, PaymentStatusChangedEvent> kafkaTemplate; // ⬅️ Добавлен generic тип

    public PaymentStatusService(PaymentRepository paymentRepository,
                                KafkaTemplate<String, PaymentStatusChangedEvent> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

 /**
     * Обновление статуса платежа (например, операционистом)
     * Публикует событие payment.status.changed
 */
    @Transactional
    public void updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentStatus oldStatus = payment.getStatus();

        // Обновляем статус
        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepository.save(payment);

        // Публикуем событие в Kafka
        PaymentStatusChangedEvent event = new PaymentStatusChangedEvent(
                payment.getId(),
                payment.getClientId(),
                oldStatus.name(),
                newStatus.name(),
                reason,
                LocalDateTime.now()
        );

        kafkaTemplate.send("payment.status.changed", payment.getId().toString(), event);
    }
}