package com.bank.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class AccountTransferDto {

    private Long id;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "\\d{20}", message = "Account number must be exactly 20 digits")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String purpose;

    private Long accountDetailsId;

    /** Идентификатор клиента. Пробрасывается в entity и notification-события. */
    private String clientId;
}
