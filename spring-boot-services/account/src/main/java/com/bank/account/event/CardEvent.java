package com.bank.account.event;

import com.bank.account.enums.CardStatus;
import com.bank.account.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kafka event payload for credit card events.
 */
 /**
 * <p>Used for topics: card.created, card.blocked, card.unblocked, card.limit.changed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardEvent {

    private Long cardId;
    private String maskedCardNumber;
    private Long accountId;
 /**
     * Идентификатор клиента — нужен notification-service для lookup push-токена.
     * Заполняется из Account.clientId при создании события в CardEventProducer.
     * Если null (legacy-данные), notification-service использует accountId.toString() как fallback.
 */
    private String clientId;
    private String cardholderName;
    private CardType cardType;
    private CardStatus status;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private LocalDate expiryDate;
    private LocalDateTime eventTime;
    private String eventType;
}
