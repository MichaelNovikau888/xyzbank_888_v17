package com.bank.antifraud.util;

import lombok.Getter;

@Getter
public enum SuspiciousTransferUtil {
    PHONE_NUMBER("+79998887766"),
    CARD_NUMBER("1234567890123456"),
    ACCOUNT_NUMBER("40817810099910004321"),
    AMOUNT_HEADER("10000.00"),
    UNKNOWN_FIELD("some_value");

   final private String type;

    SuspiciousTransferUtil(String type) {
        this.type = type;
    }
}

