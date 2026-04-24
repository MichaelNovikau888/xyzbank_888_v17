package com.bank.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreatePaymentRequest {

    @NotBlank(message = "Recipient account is required")
    @Pattern(regexp = "\\d{20}", message = "Account must be 20 digits")
    private String recipientAccount;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @Size(max = 500, message = "Description too long")
    private String description;

    // Constructors
    public CreatePaymentRequest() {}

    public CreatePaymentRequest(String recipientAccount, BigDecimal amount,
                                String currency, String description) {
        this.recipientAccount = recipientAccount;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    // Getters and setters
    public String getRecipientAccount() { return recipientAccount; }
    public void setRecipientAccount(String recipientAccount) { this.recipientAccount = recipientAccount; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}