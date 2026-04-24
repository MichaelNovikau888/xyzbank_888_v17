package com.bank.antifraud.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


@Table("audit")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class Audit {
    @Id
    private Long id;

    @Column("entity_type")
    private String entityType;

    @Column("operation_type")
    private String operationType;

    @Column("created_by")
    private String createdBy;

    @Column("modified_by")
    private String modifiedBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("modified_at")
    private LocalDateTime modifiedAt;

    @Column("new_entity_json")
    private String newEntityJson;

    @Column("entity_json")
    private String entityJson;
}
