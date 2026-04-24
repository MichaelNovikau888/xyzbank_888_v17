package com.bank.transfer.entity;

import com.bank.transfer.enums.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "card_transfer", schema = "transfer")
@Data
public class CardTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number")
    private String cardNumber;

    @Column(name = "amount", precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "account_details_id")
    private Long accountDetailsId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private TransferStatus status = TransferStatus.CREATED;
}
