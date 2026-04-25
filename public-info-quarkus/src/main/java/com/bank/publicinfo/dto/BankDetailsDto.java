package com.bank.publicinfo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BankDetailsDto {
    private Long id;
    @NotNull
    private Long bik;
    @NotNull
    private Long inn;
    @NotNull
    private String kpp;
    @NotNull
    private String corAccount;
    @Size(max = 180)
    @NotNull
    private String city;
    @Size(max = 155)
    @NotNull
    private String jointStockCompany;
    @Size(max = 80)
    @NotNull
    private String name;
}
