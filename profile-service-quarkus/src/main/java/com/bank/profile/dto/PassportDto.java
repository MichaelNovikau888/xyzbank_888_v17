package com.bank.profile.dto;
import lombok.Data;
import java.time.LocalDate;

@Data
public class PassportDto {
    private Long id;
    private Long series;
    private Long number;
    private String lastName;
    private String firstName;
    private String middleName;
    private String gender;
    private LocalDate birthDate;
    private String issuedBy;
    private LocalDate dateOfIssue;
    private Integer divisionCode;
}
