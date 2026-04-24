package com.bank.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_details", schema = "account")
public class Account {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passport_id")
    @NotNull
    private Long passportId;

    @Column(name = "account_number")
    @NotNull
    private String accountNumber;

    @Column(name = "bank_details_id")
    @NotNull
    private Long bankDetailsId;

    @Column(name = "money")
    @NotNull
    private BigDecimal money;

    @Column(name = "negative_balance")
    private boolean negativeBalance;

    @Column(name = "profile_id")
    @NotNull
    private Long profileId;
}
