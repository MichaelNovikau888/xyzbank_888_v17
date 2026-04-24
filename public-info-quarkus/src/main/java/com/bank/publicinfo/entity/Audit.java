package com.bank.publicinfo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "audit", schema = "public_info")
public class Audit extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 40)
    @NotNull
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;
    @Size(max = 255)
    @NotNull
    @Column(name = "operation_type", nullable = false)
    private String operationType;
    @Size(max = 255)
    @NotNull
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Size(max = 255)
    @Column(name = "modified_by")
    private String modifiedBy;
    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
    @Column(name = "new_entity_json", columnDefinition = "text")
    private String newEntityJson;
    @NotNull
    @Column(name = "entity_json", nullable = false, columnDefinition = "text")
    private String entityJson;
}
