package com.bank.antifraud.enums;

/**
 * Решение антифрод-системы по переводу.
 */
 /**
 * ALLOW  — перевод разрешён, продолжаем исполнение.
 * REVIEW — сумма пограничная, требуется ручная проверка оператором.
 * BLOCK  — перевод заблокирован, исполнение запрещено.
 */
public enum FraudDecision {
    ALLOW,
    REVIEW,
    BLOCK
}
