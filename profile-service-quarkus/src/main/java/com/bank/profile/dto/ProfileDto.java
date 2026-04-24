package com.bank.profile.dto;
import lombok.Data;
import jakarta.validation.constraints.Email;

@Data
public class ProfileDto {
    private Long id;
    private String phoneNumber;
    @Email private String email;
    private String nameOnCard;
    /** СНИЛС — страховой номер индивидуального лицевого счёта */
    private Long snils;
    /** ИНН — идентификационный номер налогоплательщика */
    private Long inn;
    private PassportDto passport;
}
