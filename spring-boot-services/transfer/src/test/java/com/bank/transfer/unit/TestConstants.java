package com.bank.transfer.unit;

import com.bank.transfer.enums.EntityType;
import com.bank.transfer.enums.OperationType;

import java.math.BigDecimal;

public final class TestConstants {
    private TestConstants() {
        // Приватный конструктор для предотвращения создания экземпляров
    }

    public static final String ACCOUNT_NUMBER  = "12345678901234567890";
    public static final String PHONE_NUMBER    = "79001234567";
    public static final String CARD_NUMBER     = "1234567890123456";
    public static final BigDecimal AMOUNT      = BigDecimal.valueOf(100.00);
    public static final Long ID                = 1L;
    public static final Long ACCOUNT_DETAILS_ID = 1L;
    public static final String CLIENT_ID       = "client-42";
    public static final String PURPOSE         = "Тест перевода";
    public static final String SYSTEM_USER     = "system";
    public static final OperationType OPERATION_TYPE_CREATE = OperationType.CREATE;
    public static final EntityType ENTITY_TYPE_PHONE_TRANSFER = EntityType.PHONE_TRANSFER;
}