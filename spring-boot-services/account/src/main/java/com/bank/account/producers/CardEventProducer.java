package com.bank.account.producers;

import com.bank.account.entity.CreditCard;
import com.bank.account.event.CardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka producer for credit card lifecycle events.
 */
 /**
 * <p>Topics:
 * <ul>
 *   <li>{@code card.created} — новая карта выпущена</li>
 *   <li>{@code card.blocked} — карта заблокирована</li>
 *   <li>{@code card.unblocked} — карта разблокирована</li>
 *   <li>{@code card.limit.changed} — изменён лимит</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardEventProducer {

    private static final String CARD_CREATED_TOPIC = "card.created";
    private static final String CARD_BLOCKED_TOPIC = "card.blocked";
    private static final String CARD_UNBLOCKED_TOPIC = "card.unblocked";
    private static final String CARD_LIMIT_CHANGED_TOPIC = "card.limit.changed";

    private final KafkaTemplate<String, CardEvent> cardEventKafkaTemplate;

    public void sendCardCreated(CreditCard card) {
        send(CARD_CREATED_TOPIC, card, "CREATED");
    }

    public void sendCardBlocked(CreditCard card) {
        send(CARD_BLOCKED_TOPIC, card, "BLOCKED");
    }

    public void sendCardUnblocked(CreditCard card) {
        send(CARD_UNBLOCKED_TOPIC, card, "UNBLOCKED");
    }

    public void sendCardLimitChanged(CreditCard card) {
        send(CARD_LIMIT_CHANGED_TOPIC, card, "LIMIT_CHANGED");
    }

    private void send(String topic, CreditCard card, String eventType) {
        CardEvent event = toEvent(card, eventType);
        log.info("Publishing {} event for card ID: {}", eventType, card.getId());
        cardEventKafkaTemplate.send(topic, String.valueOf(card.getId()), event);
    }

    private CardEvent toEvent(CreditCard card, String eventType) {
        // Mask card number for the event payload (never send plain number)
        String masked = card.getCardNumber() != null && card.getCardNumber().length() == 16
                ? card.getCardNumber().substring(0, 4) + " **** **** " + card.getCardNumber().substring(12)
                : "****";

        return CardEvent.builder()
                .cardId(card.getId())
                .maskedCardNumber(masked)
                .accountId(card.getAccountId())
                // clientId: используем accountId.toString() как идентификатор владельца карты.
                // Когда в Account появится поле clientId — заменить на card.getAccount().getClientId().
                .clientId(card.getAccountId() != null ? card.getAccountId().toString() : null)
                .cardholderName(card.getCardholderName())
                .cardType(card.getCardType())
                .status(card.getStatus())
                .dailyLimit(card.getDailyLimit())
                .monthlyLimit(card.getMonthlyLimit())
                .expiryDate(card.getExpiryDate())
                .eventTime(LocalDateTime.now())
                .eventType(eventType)
                .build();
    }
}
