package com.bank.publicinfo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LicenseDto {
    private Long   id;
    @NotNull private byte[] photo;
    @NotNull private Long   bankDetailsId;
}
