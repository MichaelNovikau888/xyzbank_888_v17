package com.bank.antifraud.dto;

import com.bank.antifraud.enums.FraudDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Событие ответа антифрода на проверку платежа.
 * Публикуется в топик payment.antifraud.response.
 */
 /**
 * paymentId    — id платежа из payment-api (из запроса)
 * transferType — тип перевода: ACCOUNT / CARD / PHONE
 * transferId   — id перевода внутри antifraud
 * decision     — итоговое решение: ALLOW / REVIEW / BLOCK
 * reason       — человекочитаемая причина решения
 * riskScore    — числовой скор риска 0–100 (расширение в будущем)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AntifraudResponseEvent {

    private Long paymentId;
    private String transferType;
    private Integer transferId;
    private FraudDecision decision;
    private String reason;
    private int riskScore;
}
