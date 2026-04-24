package com.bank.account.entity;

import com.bank.account.enums.CardStatus;
import com.bank.account.enums.CardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Credit card entity.
 */
 /**
 * <p>Represents a credit card linked to a bank account.
 * Supports operations: blocking, limit management, etc.
 */
@Entity
@Table(name = "credit_card", schema = "account")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "card_number", nullable = false, unique = true, length = 16)
    @NotNull
    @Size(min = 16, max = 16)
    private String cardNumber;

    @Column(name = "account_id", nullable = false)
    @NotNull
    private Long accountId;

    @Column(name = "cardholder_name", nullable = false, length = 100)
    @NotNull
    @Size(max = 100)
    private String cardholderName;

    @Column(name = "expiry_date", nullable = false)
    @NotNull
    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;

    @Column(name = "cvv_hash", nullable = false)
    @NotNull
    private String cvvHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    @NotNull
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "daily_limit", precision = 20, scale = 2)
    private BigDecimal dailyLimit = new BigDecimal("100000.00");

    @Column(name = "monthly_limit", precision = 20, scale = 2)
    private BigDecimal monthlyLimit = new BigDecimal("1000000.00");

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

 /**
     * Check if card is active and not expired.
 */
    public boolean isValid() {
        return status == CardStatus.ACTIVE
                && expiryDate.isAfter(LocalDate.now());
    }

 /**
     * Block the card.
 */
    public void block() {
        this.status = CardStatus.BLOCKED;
    }

 /**
     * Unblock the card.
 */
    public void unblock() {
        if (isExpired()) {
            throw new IllegalStateException("Cannot unblock expired card");
        }
        this.status = CardStatus.ACTIVE;
    }

 /**
     * Check if card is expired.
 */
    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }
}
