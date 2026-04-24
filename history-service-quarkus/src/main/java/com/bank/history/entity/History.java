package com.bank.history.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(
        name = "history",
        schema = "history",
        indexes = {
                @Index(name = "idx_history_content_hash", columnList = "content_hash")
        }
)
public class History extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_audit_id")
    private Long transferAuditId;
    @Column(name = "profile_audit_id")
    private Long profileAuditId;
    @Column(name = "account_audit_id")
    private Long accountAuditId;
    @Column(name = "anti_fraud_audit_id")
    private Long antiFraudAuditId;
    @Column(name = "public_bank_info_audit_id")
    private Long publicBankInfoAuditId;
    @Column(name = "authorization_audit_id")
    private Long authorizationAuditId;

    @Column(name = "event_type", length = 100)
    private String eventType;
    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;
    @Column(name = "service_name", length = 50)
    private String serviceName;
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

 /**
     * SHA-256 хэш от (serviceName + eventType + eventData).
     * Используется для deduplicate при at-least-once Kafka delivery.
     * Уникальный индекс idx_history_content_hash защищает от дублей на уровне БД.
 */
    @Column(name = "content_hash", length = 64, unique = true)
    private String contentHash;
}
