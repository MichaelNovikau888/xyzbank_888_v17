package com.bank.notification.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentCreatedEvent {

    public Long paymentId;
    public String clientId;
    public String recipientAccount;
    public BigDecimal amount;
    public String currency;
    public String status;
    public LocalDateTime createdAt;
    /** Последние 4 цифры счёта/карты — для отображения «KARTA #4321» в push. */
    public String cardLastFour;

    public PaymentCreatedEvent() {
    }

    public PaymentCreatedEvent(Long paymentId, String clientId, String recipientAccount,
                              BigDecimal amount, String currency, String status,
                              LocalDateTime createdAt) {
        this.paymentId = paymentId;
        this.clientId = clientId;
        this.recipientAccount = recipientAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }
}
