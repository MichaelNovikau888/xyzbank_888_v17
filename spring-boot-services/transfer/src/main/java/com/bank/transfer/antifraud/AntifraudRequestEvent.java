package com.bank.transfer.antifraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Событие запроса на антифрод-проверку перевода.
 */
 /**
 * Публикуется transfer-service в топик: transfer.antifraud.check
 * Antifraud отвечает в:                 transfer.antifraud.response
 */
 /**
 * transferId   — id перевода в transfer-service (нужен для ответа)
 * transferType — ACCOUNT / CARD / PHONE (antifraud выбирает анализатор)
 * amount       — сумма (antifraud применяет пороги: ≤10k=ALLOW, ≤50k=REVIEW, >50k=BLOCK)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AntifraudRequestEvent {

    /** ID перевода внутри transfer-service. */
    private Long   transferId;

    /** ACCOUNT / CARD / PHONE. */
    private String transferType;

    /** Сумма перевода — основной критерий антифрода. */
    private BigDecimal amount;

    /** Назначение платежа (опционально, для audit-лога antifraud). */
    private String purpose;
}
