package com.bank.notification.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Запись о доставленном уведомлении по финальному статусу платежа/перевода.
 */
 /**
 * Хранит только конечные статусы: COMPLETED, FAILED, CANCELLED, BLOCKED.
 * Промежуточные (CREATED, PROCESSING, REVIEW) не записываются.
 */
 /**
 * Таблица: notification.notification_records
 * REST:    GET /api/v1/notifications/{clientId}
 */
@Entity
@Table(name = "notification_records", schema = "notification")
public class NotificationRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notification_record_seq")
    @SequenceGenerator(name = "notification_record_seq",
                       sequenceName = "notification.notification_record_seq",
                       allocationSize = 50)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

 /**
     * Финальный статус: COMPLETED, FAILED, CANCELLED, BLOCKED.
 */
    @Column(name = "final_status", nullable = false, length = 20)
    private String finalStatus;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "recipient_account", length = 50)
    private String recipientAccount;

 /**
     * Причина: для FAILED — причина блокировки; для CANCELLED — причина отмены.
 */
    @Column(length = 500)
    private String reason;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public NotificationRecord() {}

    public NotificationRecord(Long paymentId, String clientId, String finalStatus,
                               BigDecimal amount, String currency,
                               String recipientAccount, String reason) {
        this.paymentId        = paymentId;
        this.clientId         = clientId;
        this.finalStatus      = finalStatus;
        this.amount           = amount;
        this.currency         = currency;
        this.recipientAccount = recipientAccount;
        this.reason           = reason;
    }

    @PrePersist
    void prePersist() {
        this.notifiedAt = LocalDateTime.now();
    }

    // ── Panache finders ───────────────────────────────────────────────────────

    public static List<NotificationRecord> findByClientIdPaged(String clientId, int page, int size) {
        return find("clientId = ?1 ORDER BY notifiedAt DESC", clientId)
                .page(page, size)
                .list();
    }

    public static long countByClientId(String clientId) {
        return count("clientId", clientId);
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public Long getPaymentId()                   { return paymentId; }
    public String getClientId()                  { return clientId; }
    public String getFinalStatus()               { return finalStatus; }
    public BigDecimal getAmount()                { return amount; }
    public String getCurrency()                  { return currency; }
    public String getRecipientAccount()          { return recipientAccount; }
    public String getReason()                    { return reason; }
    public LocalDateTime getNotifiedAt()         { return notifiedAt; }

    public void setPaymentId(Long v)             { this.paymentId = v; }
    public void setClientId(String v)            { this.clientId = v; }
    public void setFinalStatus(String v)         { this.finalStatus = v; }
    public void setAmount(BigDecimal v)          { this.amount = v; }
    public void setCurrency(String v)            { this.currency = v; }
    public void setRecipientAccount(String v)    { this.recipientAccount = v; }
    public void setReason(String v)              { this.reason = v; }
    public void setNotifiedAt(LocalDateTime v)   { this.notifiedAt = v; }
}
