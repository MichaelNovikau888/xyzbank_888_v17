package com.bank.publicinfo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ATMDto {
    private Long id;
    @Size(max = 370)
    @NotNull
    private String address;
    private LocalTime startOfWork;
    private LocalTime endOfWork;
    @NotNull
    private Boolean allHours;
    @NotNull
    private Long branchId;
}
