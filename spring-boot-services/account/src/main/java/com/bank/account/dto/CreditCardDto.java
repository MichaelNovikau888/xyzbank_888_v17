package com.bank.account.dto;

import com.bank.account.enums.CardStatus;
import com.bank.account.enums.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for credit card information.
 */
 /**
 * <p>Used for API responses. Does not expose sensitive data like CVV.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardDto {

    private Long id;

    /** Маскированный номер карты (e.g., "1234 **** **** 5678") */
    private String maskedCardNumber;

    private Long accountId;
    private String cardholderName;
    private LocalDate expiryDate;
    private CardType cardType;
    private CardStatus status;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
