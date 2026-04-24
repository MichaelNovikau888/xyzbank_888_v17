package com.bank.account.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Credit card status.
 */
@Getter
@RequiredArgsConstructor
public enum CardStatus {
    ACTIVE("Active"),
    BLOCKED("Blocked"),
    EXPIRED("Expired"),
    LOST("Lost"),
    STOLEN("Stolen");

    private final String displayName;
}
