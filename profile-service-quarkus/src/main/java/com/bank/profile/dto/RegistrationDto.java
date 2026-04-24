package com.bank.profile.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationDto {
    private Long id;
    @Size(max = 166) @NotNull
    private String country;
    @Size(max = 160)
    private String city;
    @NotNull
    private Long index;
    @Size(max = 160)
    private String region;
    @Size(max = 160)
    private String district;
    @Size(max = 160)
    private String locality;
    @Size(max = 160)
    private String street;
    @Size(max = 20)
    private String houseNumber;
    @Size(max = 20)
    private String houseBlock;
    @Size(max = 40)
    private String flatNumber;

    public static RegistrationDto Empty() {
        RegistrationDto dto = new RegistrationDto();
        dto.country = "Not Set";
        dto.city = "";
        dto.index = 0L;
        dto.region = "";
        dto.district = "";
        dto.locality = "";
        dto.street = "";
        dto.houseNumber = "";
        dto.houseBlock = "";
        dto.flatNumber = "";
        return dto;
    }
}
