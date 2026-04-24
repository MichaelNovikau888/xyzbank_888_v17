package com.bank.profile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDto {
    @NotNull
    private LocalDateTime timestamp;
    @NotNull
    private String message;

    public static ErrorDto of(String message) {
        return new ErrorDto(LocalDateTime.now(), message);
    }
}
