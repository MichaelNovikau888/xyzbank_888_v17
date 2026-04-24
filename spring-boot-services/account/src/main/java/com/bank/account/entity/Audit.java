package com.bank.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "audit", schema = "account")
public class Audit {
    private static final int MAX_LENGTH_SHORT = 50;
    private static final int MAX_LENGTH_LONG = 255;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type")
    @Size(max = MAX_LENGTH_SHORT)
    private String entityType;

    @Column(name = "operation_type")
    @Size(max = MAX_LENGTH_LONG)
    private String operationType;

    @Column(name = "created_by")
    @Size(max = MAX_LENGTH_LONG)
    private String createdBy;

    @Column(name = "modified_by")
    @Size(max = MAX_LENGTH_LONG)
    private String modifiedBy;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "modified_at")
    private Timestamp modifiedAt;

    @Column(name = "new_entity_json")
    private String newEntityJson;

    @Column(name = "entity_json")
    private String entityJson;
}
