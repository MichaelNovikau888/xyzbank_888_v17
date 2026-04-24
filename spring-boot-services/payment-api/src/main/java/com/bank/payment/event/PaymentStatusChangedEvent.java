package com.bank.payment.event;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Событие изменения статуса платежа
 * Публикуется в топик payment.status.changed
 */
@Data
public class PaymentStatusChangedEvent {

    private Long paymentId;
    private String clientId;
    private String oldStatus;
    private String newStatus;
    private String reason;
    private LocalDateTime changedAt;

    // Constructors
    public PaymentStatusChangedEvent() {
    }

    public PaymentStatusChangedEvent(Long paymentId, String clientId, String oldStatus,
                                    String newStatus, String reason, LocalDateTime changedAt) {
        this.paymentId = paymentId;
        this.clientId = clientId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedAt = changedAt;
    }
}