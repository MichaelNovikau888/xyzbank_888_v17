package com.bank.report.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие перевода из transfer-service.
 * Публикуется в топик: transfer.notification
 * status: CREATED / COMPLETED / BLOCKED / CANCELLED
 */
public class TransferNotificationEvent {

    public Long          transferId;
    public Long          clientId;       // Profile.id — Long
    public String        transferType;   // ACCOUNT / CARD / PHONE
    public String        status;
    public String        reason;
    public BigDecimal    amount;
    public String        currency;
    public String        recipientDisplay;
    public String        purpose;
    public LocalDateTime occurredAt;

    public TransferNotificationEvent() {}
}
