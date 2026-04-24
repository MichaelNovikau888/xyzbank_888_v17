package com.bank.notification.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие жизненного цикла банковской карты.
 */
 /**
 * Публикуется account-service в топики:
 *   card.created, card.blocked, card.unblocked, card.limit.changed
 */
 /**
 * eventType: CREATED / BLOCKED / UNBLOCKED / LIMIT_CHANGED
 */
public class CardNotificationEvent {

    public Long          cardId;
    public String        maskedCardNumber;   // формат: «4276 **** **** 1234»
    public Long          accountId;
 /**
     * Идентификатор клиента для lookup push-токена.
     * Может быть null в событиях от старой версии account-service —
     * в этом случае CardNotificationConsumer использует accountId.toString() как fallback.
 */
    public String        clientId;
    public String        cardholderName;
    public String        cardType;           // DEBIT / CREDIT / VIRTUAL
    public String        status;             // ACTIVE / BLOCKED / EXPIRED
    public BigDecimal    dailyLimit;
    public BigDecimal    monthlyLimit;
    public String        expiryDate;
    public LocalDateTime eventTime;
    public String        eventType;          // CREATED / BLOCKED / UNBLOCKED / LIMIT_CHANGED

    public CardNotificationEvent() {}
}
