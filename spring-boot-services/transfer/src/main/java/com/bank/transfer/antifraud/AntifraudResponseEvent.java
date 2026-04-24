package com.bank.transfer.antifraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Событие ответа антифрода на проверку перевода.
 */
 /**
 * Публикуется antifraud в топик: transfer.antifraud.response
 * Получатель:                    transfer-service (AntifraudResponseConsumer)
 */
 /**
 * decision: ALLOW / REVIEW / BLOCK
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AntifraudResponseEvent {

    /** ID перевода из запроса — по нему transfer находит перевод. */
    private Long   transferId;

    /** ACCOUNT / CARD / PHONE. */
    private String transferType;

 /**
     * Решение: ALLOW / REVIEW / BLOCK.
     * Передаётся строкой чтобы не создавать зависимость от enum antifraud.
 */
    private String decision;

    /** Человекочитаемая причина (для BLOCK/REVIEW — пишется в history). */
    private String reason;

    /** Числовой скор риска 0–100. */
    private int    riskScore;
}
