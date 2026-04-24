package com.bank.report.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие изменения статуса платежа.
 * clientId — Long = Profile.id.
 */
public class PaymentStatusChangedEvent {

    public Long          paymentId;
    public Long          clientId;       // Profile.id — Long
    public String        recipientAccount;
    public BigDecimal    amount;
    public String        currency;
    public String        oldStatus;
    public String        newStatus;
    public LocalDateTime statusChangedAt;
    public String        reason;

    public PaymentStatusChangedEvent() {}
}
