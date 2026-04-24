package com.bank.transfer.dto;

import com.bank.transfer.enums.EntityType;
import com.bank.transfer.enums.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditDto {
    private Long id;
    private EntityType entityType;
    private OperationType operationType;
    private String createdBy;
    private String modifiedBy;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private String newEntityJson;
    private String entityJson;
}
