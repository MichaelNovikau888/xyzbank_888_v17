package com.bank.notification.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие изменения статуса платежа
 * Отправляется в топик: payment.status.changed
 */
public class PaymentStatusChangedEvent {

    public Long paymentId;
    public String clientId;
    public String recipientAccount;
    public BigDecimal amount;
    public String currency;
    
    public String oldStatus;  // Предыдущий статус
    public String newStatus;  // Новый статус
    
    public LocalDateTime statusChangedAt;
    public String reason;  // Причина изменения (для FAILED, CANCELLED)

    public PaymentStatusChangedEvent() {
    }

    public PaymentStatusChangedEvent(Long paymentId, String clientId, String recipientAccount,
                                    BigDecimal amount, String currency,
                                    String oldStatus, String newStatus,
                                    LocalDateTime statusChangedAt, String reason) {
        this.paymentId = paymentId;
        this.clientId = clientId;
        this.recipientAccount = recipientAccount;
        this.amount = amount;
        this.currency = currency;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.statusChangedAt = statusChangedAt;
        this.reason = reason;
    }
}