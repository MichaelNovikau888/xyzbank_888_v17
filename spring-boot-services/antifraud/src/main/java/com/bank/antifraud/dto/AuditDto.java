package com.bank.antifraud.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class AuditDto {
    long id;

    @NotNull
    String entityType;

    @NotNull
    String operationType;

    @NotNull
    String createdBy;

    String modifiedBy;

    @NotNull
    LocalDateTime createdAt;

    LocalDateTime modifiedAt;

    String newEntityJson;
    @NotNull
    String entityJson;
}
