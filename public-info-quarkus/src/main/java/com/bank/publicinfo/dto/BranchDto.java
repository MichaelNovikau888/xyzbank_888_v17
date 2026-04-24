package com.bank.publicinfo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalTime;

@Data
public class BranchDto {
    private Long      id;
    @Size(max = 370) @NotNull private String    address;
    @NotNull private String    phoneNumber;
    @Size(max = 250) @NotNull private String    city;
    @NotNull private LocalTime startOfWork;
    @NotNull private LocalTime endOfWork;
}
