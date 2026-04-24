package com.bank.authorization.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Audit {

    private static final int ENTITY_TYPE_LENGTH = 40;
    private static final int SEVERAL_FIELDS_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_type", length = ENTITY_TYPE_LENGTH, nullable = false)
    private String entityType;

    @Column(name = "operation_type", length = SEVERAL_FIELDS_LENGTH, nullable = false)
    private String operationType;

    @Column(name = "created_by", length = SEVERAL_FIELDS_LENGTH, nullable = false)
    private String createdBy;

    @Column(name = "modified_by", length = SEVERAL_FIELDS_LENGTH)
    private String modifiedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "new_entity_json", columnDefinition = "TEXT")
    private String newEntityJson;

    @Column(name = "entity_json", nullable = false, columnDefinition = "TEXT")
    private String entityJson;
}
