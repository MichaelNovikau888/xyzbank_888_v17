package com.bank.publicinfo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditDto {
    private Long          id;
    @NotNull private String        entityType;
    @NotNull private String        operationType;
    @NotNull private String        createdBy;
    private String        modifiedBy;
    @NotNull private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private String        newEntityJson;
    @NotNull private String        entityJson;
}
