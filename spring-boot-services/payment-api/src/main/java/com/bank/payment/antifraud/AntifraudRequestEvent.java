package com.bank.payment.antifraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Запрос на антифрод-проверку.
 * Публикуется payment-api в топик payment.antifraud.check.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AntifraudRequestEvent {
    private Long paymentId;
    private String clientId;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;
    /** ACCOUNT / CARD / PHONE. Сейчас payment-api всегда шлёт ACCOUNT. */
    private String transferType;
}
