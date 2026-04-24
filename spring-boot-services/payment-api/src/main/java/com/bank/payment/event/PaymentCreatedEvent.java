package com.bank.payment.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие создания платежа для Kafka
 * Публикуется в топик payment.created
 */
public class PaymentCreatedEvent {

    private Long paymentId;
    private String clientId;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    /** Последние 4 цифры счёта/карты — для отображения в push-уведомлении (например, #4321). */
    private String cardLastFour;

    // Constructors
    public PaymentCreatedEvent() {
    }

    public PaymentCreatedEvent(Long paymentId, String clientId, String recipientAccount,
                              BigDecimal amount, String currency, String status,
                              LocalDateTime createdAt) {
        this(paymentId, clientId, recipientAccount, amount, currency, status, createdAt, null);
    }

    public PaymentCreatedEvent(Long paymentId, String clientId, String recipientAccount,
                              BigDecimal amount, String currency, String status,
                              LocalDateTime createdAt, String cardLastFour) {
        this.paymentId = paymentId;
        this.clientId = clientId;
        this.recipientAccount = recipientAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.cardLastFour = cardLastFour;
    }

    // Getters and setters
    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRecipientAccount() {
        return recipientAccount;
    }

    public void setRecipientAccount(String recipientAccount) {
        this.recipientAccount = recipientAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
}
