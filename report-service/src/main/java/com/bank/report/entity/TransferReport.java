package com.bank.report.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Запись о переводе для отчётности.
 */
 /**
 * clientId — Long = Profile.id (аналогично PaymentReport).
 * transferType: ACCOUNT / CARD / PHONE.
 * status: CREATED / COMPLETED / BLOCKED / CANCELLED.
 */
 /**
 * Партиционирование: RANGE по report_date (день).
 */
@Entity
@Table(name = "transfer_reports", schema = "report")
public class TransferReport extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, unique = true)
    private Long transferId;

    /** Profile.id клиента — Long, как и в PaymentReport. */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "transfer_type", nullable = false, length = 10)
    private String transferType;   // ACCOUNT / CARD / PHONE

    @Column(nullable = false, length = 20)
    private String status;         // CREATED / COMPLETED / BLOCKED / CANCELLED

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "recipient_display", length = 100)
    private String recipientDisplay;

    @Column(length = 500)
    private String reason;

    /** Ключ партиционирования. */
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // ── Panache queries ───────────────────────────────────────────────────────

    public static List<TransferReport> findByDate(LocalDate date) {
        return list("reportDate", date);
    }

    public static List<TransferReport> findByClientAndDate(Long clientId, LocalDate date) {
        return list("clientId = ?1 AND reportDate = ?2", clientId, date);
    }

    public static List<TransferReport> findByClientAndDateRange(
            Long clientId, LocalDate from, LocalDate to) {
        return list("clientId = ?1 AND reportDate >= ?2 AND reportDate <= ?3", clientId, from, to);
    }

    public static List<TransferReport> findByDateRange(LocalDate from, LocalDate to) {
        return list("reportDate >= ?1 AND reportDate <= ?2", from, to);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public Long getTransferId()                { return transferId; }
    public Long getClientId()                  { return clientId; }
    public String getTransferType()            { return transferType; }
    public String getStatus()                  { return status; }
    public BigDecimal getAmount()              { return amount; }
    public String getCurrency()                { return currency; }
    public String getRecipientDisplay()        { return recipientDisplay; }
    public String getReason()                  { return reason; }
    public LocalDate getReportDate()           { return reportDate; }
    public LocalDateTime getOccurredAt()       { return occurredAt; }

    public void setId(Long id)                          { this.id = id; }
    public void setTransferId(Long transferId)          { this.transferId = transferId; }
    public void setClientId(Long clientId)              { this.clientId = clientId; }
    public void setTransferType(String transferType)    { this.transferType = transferType; }
    public void setStatus(String status)                { this.status = status; }
    public void setAmount(BigDecimal amount)            { this.amount = amount; }
    public void setCurrency(String currency)            { this.currency = currency; }
    public void setRecipientDisplay(String v)           { this.recipientDisplay = v; }
    public void setReason(String reason)                { this.reason = reason; }
    public void setReportDate(LocalDate reportDate)     { this.reportDate = reportDate; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
