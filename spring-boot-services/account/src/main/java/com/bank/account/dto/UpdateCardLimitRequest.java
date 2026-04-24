package com.bank.account.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for updating card limits.
 */
@Data
public class UpdateCardLimitRequest {

    @Positive(message = "Daily limit must be positive")
    private BigDecimal dailyLimit;

    @Positive(message = "Monthly limit must be positive")
    private BigDecimal monthlyLimit;
}
