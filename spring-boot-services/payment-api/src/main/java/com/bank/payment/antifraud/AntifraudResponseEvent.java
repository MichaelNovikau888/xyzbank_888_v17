package com.bank.payment.antifraud;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ антифрода на проверку платежа.
 * Потребляется из топика payment.antifraud.response.
 */
 /**
 * decision: ALLOW / REVIEW / BLOCK
 */
@Getter @Setter @NoArgsConstructor
public class AntifraudResponseEvent {
    private Long paymentId;
    private String transferType;
    private Integer transferId;
    private String decision;   // строка — antifraud из другого сервиса, enum не шарим
    private String reason;
    private int riskScore;
}
