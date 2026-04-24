package com.bank.account.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Credit card types supported by the bank.
 */
@Getter
@RequiredArgsConstructor
public enum CardType {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    MIR("Mir"),
    MAESTRO("Maestro");

    private final String displayName;
}
