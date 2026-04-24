package com.bank.authorization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditDto {

    private Long id;

    @NotBlank(message = "Тип сущности не может быть пустым")
    @Size(max = 40, message = "Тип сущности не должен превышать 40 символов")
    private String entityType;

    @NotBlank(message = "Тип операции не может быть пустым")
    @Size(max = 255, message = "Тип операции не должен превышать 255 символов")
    private String operationType;

    @NotBlank(message = "Создатель записи не может быть пустым")
    @Size(max = 255, message = "Имя создателя не должно превышать 255 символов")
    private String createdBy;

    @Size(max = 255, message = "Имя модификатора не должно превышать 255 символов")
    private String modifiedBy;

    @NotNull(message = "Дата создания не может быть null")
    @PastOrPresent(message = "Дата создания не может быть в будущем")
    private LocalDateTime createdAt;

    @PastOrPresent(message = "Дата модификации не может быть в будущем")
    private LocalDateTime modifiedAt;

    private String newEntityJson;

    @NotNull(message = "JSON сущности не может быть null")
    private String entityJson;
}
