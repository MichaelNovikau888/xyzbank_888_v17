package com.bank.notification.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие уведомления о переводе.
 * Публикуется transfer-service в топик: transfer.notification
 */
 /**
 * status: CREATED / COMPLETED / BLOCKED / CANCELLED
 */
public class TransferNotificationEvent {

    public Long          transferId;
    public String        clientId;
    public String        transferType;      // ACCOUNT / CARD / PHONE
    public String        status;            // CREATED / COMPLETED / BLOCKED / CANCELLED
    public String        reason;            // причина блокировки/отмены (null для CREATED/COMPLETED)
    public BigDecimal    amount;
    public String        currency;
    public String        recipientDisplay;  // счёт / **** 1234 / +7 9** **
    public String        purpose;           // назначение платежа
    public LocalDateTime occurredAt;

    public TransferNotificationEvent() {}
}
