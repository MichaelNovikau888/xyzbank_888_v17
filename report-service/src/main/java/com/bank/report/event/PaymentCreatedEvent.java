package com.bank.report.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие создания платежа из payment-api.
 * clientId — Long = Profile.id.
 */
public class PaymentCreatedEvent {

    public Long          paymentId;
    public Long          clientId;       // Profile.id — Long
    public String        recipientAccount;
    public BigDecimal    amount;
    public String        currency;
    public String        status;
    public LocalDateTime createdAt;

    public PaymentCreatedEvent() {}
}
