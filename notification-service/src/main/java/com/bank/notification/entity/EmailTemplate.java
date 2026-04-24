package com.bank.notification.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Шаблон email-уведомления.
 */
 /**
 * Таблица: notification.email_templates
 * Заполняется через import.sql (тестовые данные) и Liquibase (prod-данные).
 */
 /**
 * Плейсхолдеры в body: {payment_id}, {amount}, {currency}, {recipient_account},
 *                       {transfer_id}, {transfer_type}, {recipient_display},
 *                       {purpose}, {occurred_date}, {failure_reason},
 *                       {cancellation_reason}, {cancellation_date},
 *                       {block_reason}, {cancel_reason}, {completion_date}
 */
@Entity
@Table(name = "email_templates", schema = "notification")
public class EmailTemplate extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Уникальное имя шаблона, например "payment_completed". */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Panache finders ───────────────────────────────────────────────────────

    public static Optional<EmailTemplate> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long getId()                        { return id; }
    public String getName()                    { return name; }
    public String getSubject()                 { return subject; }
    public String getBody()                    { return body; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }

    public void setName(String v)              { this.name = v; }
    public void setSubject(String v)           { this.subject = v; }
    public void setBody(String v)              { this.body = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
    public void setUpdatedAt(LocalDateTime v)  { this.updatedAt = v; }
}
