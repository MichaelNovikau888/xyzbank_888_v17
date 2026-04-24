package com.bank.authorization.controller;

import com.bank.authorization.dto.UserRegistrationRequest;
import com.bank.authorization.dto.UserRegistrationResponse;
import com.bank.authorization.entity.User;
import com.bank.authorization.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityExistsException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер аутентификации.
 */
 /**
 * <p>Self-service регистрация клиента:
 * <pre>
 *   POST /api/v1/auth/register
 * </pre>
 */
 /**
 * <p>Все эндпоинты этого контроллера открыты без JWT
 * (добавлены в {@code SecurityConfig.permitAll()}).
 */
 /**
 * <p>Основной flow авторизации (login/validate) по-прежнему
 * работает через Kafka ({@link com.bank.authorization.handler.AuthCommandHandler}).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Self-service registration API")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRegistrationService registrationService;

 /**
     * Регистрирует нового клиента.
 */
 /**
     * <p>При успехе:
     * <ul>
     *   <li>Создаёт пользователя со статусом {@code PENDING_VERIFICATION}</li>
     *   <li>Публикует {@code UserRegistered} в топик {@code auth.user.registered}</li>
     *   <li>notification-service отправляет welcome email</li>
     * </ul>
 */
 /**
     * @return 201 Created с данными пользователя
     * @return 409 Conflict если email уже зарегистрирован
 */
    @PostMapping("/register")
    @Operation(
        summary = "Register new client",
        description = "Creates a new user account. " +
                      "Publishes UserRegistered event to auth.user.registered topic. " +
                      "notification-service sends a welcome email."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error (invalid email/password/phone)"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<UserRegistrationResponse> register(
            @Valid @RequestBody UserRegistrationRequest request) {

        try {
            User user = registrationService.registerUser(request);

            UserRegistrationResponse response = UserRegistrationResponse.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .requiresEmailVerification(true)
                    .message("Registration successful. Please check your email to verify your account.")
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (EntityExistsException e) {
            log.warn("Registration attempt with existing email: {}", request.getEmail());

            UserRegistrationResponse conflict = UserRegistrationResponse.builder()
                    .email(request.getEmail())
                    .requiresEmailVerification(false)
                    .message("Email is already registered. Please login or reset your password.")
                    .build();

            return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
        }
    }
}
