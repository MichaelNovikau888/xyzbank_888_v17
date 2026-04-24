package com.bank.profile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditDto {
    private Long id;
    @Size(max = 40) @NotNull
    private String entityType;
    @Size(max = 255) @NotNull
    private String operationType;
    @Size(max = 255) @NotNull
    private String createdBy;
    @Size(max = 255)
    private String modifiedBy;
    @NotNull
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private String newEntityJson;
    @NotNull
    private String entityJson;
}
