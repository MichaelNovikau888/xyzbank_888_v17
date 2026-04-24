package com.bank.account.dto;

import com.bank.account.enums.CardType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new credit card.
 */
@Data
public class CreateCreditCardRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotBlank(message = "Cardholder name is required")
    @Size(max = 100, message = "Cardholder name too long")
    private String cardholderName;

    @NotNull(message = "Card type is required")
    private CardType cardType;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;

    @NotBlank(message = "CVV is required")
    @Pattern(regexp = "\\d{3}", message = "CVV must be 3 digits")
    private String cvv;

    @Positive(message = "Daily limit must be positive")
    private BigDecimal dailyLimit;

    @Positive(message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;
}
