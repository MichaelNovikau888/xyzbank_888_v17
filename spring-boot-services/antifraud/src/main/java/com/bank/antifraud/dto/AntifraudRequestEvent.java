package com.bank.antifraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Событие запроса на антифрод-проверку платежа.
 * Публикуется payment-api в топик payment.antifraud.check.
 */
 /**
 * paymentId        — id платежа в payment-api (нужен для ответа)
 * clientId         — идентификатор клиента
 * recipientAccount — счёт получателя
 * amount           — сумма перевода
 * currency         — валюта
 * transferType     — тип: ACCOUNT / CARD / PHONE
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AntifraudRequestEvent {

    private Long paymentId;
    private String clientId;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;

 /**
     * Тип перевода — antifraud использует для выбора нужного анализатора.
     * Значения: ACCOUNT, CARD, PHONE.
     * payment-api определяет тип по recipientAccount (счёт → ACCOUNT, остальное по умолчанию ACCOUNT).
 */
    private String transferType;
}
