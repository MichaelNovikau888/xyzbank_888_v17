package com.bank.report.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Запись о платеже для отчётности.
 */
 /**
 * clientId — Long = Profile.id из profile-service.
 * Типовая консистентность: profile-service.Profile.id — Long,
 * поэтому clientId в отчёте тоже Long.
 */
 /**
 * Партиционирование: RANGE по report_date (день).
 * Создаётся через Liquibase, Hibernate не управляет партиционированием.
 */
@Entity
@Table(name = "payment_reports", schema = "report")
public class PaymentReport extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

 /**
     * Соответствует Profile.id (Long) из profile-service.
     * Позволяет фильтровать отчёт по конкретному клиенту банка.
 */
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "recipient_account", length = 50)
    private String recipientAccount;

    /** Ключ партиционирования — направляет запрос в нужную партицию. */
    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Panache queries ───────────────────────────────────────────────────────

    public static List<PaymentReport> findByDate(LocalDate date) {
        return list("reportDate", date);
    }

    public static List<PaymentReport> findByClientAndDate(Long clientId, LocalDate date) {
        return list("clientId = ?1 AND reportDate = ?2", clientId, date);
    }

    public static List<PaymentReport> findByClientAndDateRange(
            Long clientId, LocalDate from, LocalDate to) {
        return list("clientId = ?1 AND reportDate >= ?2 AND reportDate <= ?3", clientId, from, to);
    }

    public static List<PaymentReport> findByDateRange(LocalDate from, LocalDate to) {
        return list("reportDate >= ?1 AND reportDate <= ?2", from, to);
    }

    public static long countByDate(LocalDate date) {
        return count("reportDate", date);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public Long getPaymentId()                 { return paymentId; }
    public Long getClientId()                  { return clientId; }
    public BigDecimal getAmount()              { return amount; }
    public String getCurrency()                { return currency; }
    public String getStatus()                  { return status; }
    public String getRecipientAccount()        { return recipientAccount; }
    public LocalDate getReportDate()           { return reportDate; }
    public LocalDateTime getCreatedAt()        { return createdAt; }

    public void setId(Long id)                         { this.id = id; }
    public void setPaymentId(Long paymentId)           { this.paymentId = paymentId; }
    public void setClientId(Long clientId)             { this.clientId = clientId; }
    public void setAmount(BigDecimal amount)           { this.amount = amount; }
    public void setCurrency(String currency)           { this.currency = currency; }
    public void setStatus(String status)               { this.status = status; }
    public void setRecipientAccount(String v)          { this.recipientAccount = v; }
    public void setReportDate(LocalDate reportDate)    { this.reportDate = reportDate; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
}
