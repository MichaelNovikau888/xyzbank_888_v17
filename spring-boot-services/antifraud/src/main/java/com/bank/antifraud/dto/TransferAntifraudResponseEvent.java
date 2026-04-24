package com.bank.antifraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Событие ответа антифрода на проверку перевода от transfer-service.
 */
 /**
 * Топик: transfer.antifraud.response
 */
 /**
 * decision передаётся строкой ("ALLOW"/"REVIEW"/"BLOCK") чтобы
 * transfer-service не зависел от enum com.bank.antifraud.enums.FraudDecision.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferAntifraudResponseEvent {

    /** ID перевода из запроса — transfer-service находит перевод по нему. */
    private Long   transferId;

    /** ACCOUNT / CARD / PHONE. */
    private String transferType;

    /** Решение: "ALLOW" / "REVIEW" / "BLOCK". */
    private String decision;

    /** Причина (для BLOCK/REVIEW пишется в history). */
    private String reason;

    /** Числовой скор риска 0–100. */
    private int    riskScore;
}
