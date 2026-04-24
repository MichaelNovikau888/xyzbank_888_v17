package com.bank.antifraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Событие запроса антифрод-проверки от transfer-service.
 */
 /**
 * Топик: transfer.antifraud.check
 */
 /**
 * Аналог AntifraudRequestEvent, но с transferId вместо paymentId
 * (transfer-service не знает о платежах payment-api).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferAntifraudRequestEvent {

    /** ID перевода в transfer-service (AccountTransfer / CardTransfer / PhoneTransfer). */
    private Long       transferId;

    /** ACCOUNT / CARD / PHONE. */
    private String     transferType;

    /** Сумма — основной критерий: ≤10k=ALLOW, ≤50k=REVIEW, >50k=BLOCK. */
    private BigDecimal amount;

    /** Назначение платежа (для audit-лога). */
    private String     purpose;
}
