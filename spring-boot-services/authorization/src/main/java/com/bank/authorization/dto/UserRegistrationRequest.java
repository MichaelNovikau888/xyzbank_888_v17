package com.bank.authorization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO самостоятельной регистрации клиента.
 */
 /**
 * <p>Валидация:
 * <ul>
 *   <li>email — обязателен, корректный формат</li>
 *   <li>password — 8–255 символов, содержит upper, lower, цифру и спецсимвол</li>
 *   <li>fullName — 2–255 символов</li>
 *   <li>phoneNumber — международный формат, 10–15 цифр</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must contain at least one uppercase letter, " +
                  "one lowercase letter, one digit and one special character (@$!%*?&)"
    )
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+?[0-9]{10,15}$",
        message = "Phone number must contain 10–15 digits, optionally starting with +"
    )
    private String phoneNumber;
}
