package com.bank.authorization.service;

import com.bank.authorization.dto.UserRegistrationRequest;
import com.bank.authorization.entity.User;
import com.bank.authorization.outbox.AuthOutboxHelper;
import com.bank.authorization.repository.UserRepository;
import jakarta.persistence.EntityExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты UserRegistrationService.
 */
 /**
 * Покрывает:
 *   1. Успешная регистрация → User сохранён, outbox enqueued
 *   2. Email уже существует → EntityExistsException, User не сохранён
 *   3. Пароль хешируется перед сохранением
 *   4. Поля User заполнены корректно (role=ROLE_USER, status=PENDING_VERIFICATION)
 *   5. Outbox получает верные аргументы: topic, eventType, userId
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationService — unit tests")
class UserRegistrationServiceTest {

    @Mock private UserRepository  userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthOutboxHelper outboxHelper;

    @InjectMocks
    private UserRegistrationService registrationService;

    private UserRegistrationRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new UserRegistrationRequest(
                "ivan@example.com",
                "Password1@",
                "Иван Иванов",
                "+79161234567"
        );
    }

    // ── 1. Успешная регистрация ───────────────────────────────────────────────

    @Test
    @DisplayName("1. Валидный запрос → User сохранён, outbox enqueued")
    void registerUser_validRequest_savesUserAndEnqueuesOutbox() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1@")).thenReturn("$2a$hashed");

        User saved = new User();
        saved.setId(42L);
        saved.setEmail("ivan@example.com");
        saved.setFullName("Иван Иванов");
        saved.setPhoneNumber("+79161234567");
        saved.setRole("ROLE_USER");
        saved.setStatus("PENDING_VERIFICATION");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = registrationService.registerUser(validRequest);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getEmail()).isEqualTo("ivan@example.com");

        // outbox вызван с правильными аргументами
        verify(outboxHelper).enqueue(
                eq("auth.user.registered"),
                eq("42"),
                eq("UserRegistered"),
                any()
        );
    }

    // ── 2. Email уже существует ───────────────────────────────────────────────

    @Test
    @DisplayName("2. Email уже зарегистрирован → EntityExistsException, репозиторий не вызывается")
    void registerUser_duplicateEmail_throwsEntityExistsException() {
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerUser(validRequest))
                .isInstanceOf(EntityExistsException.class)
                .hasMessageContaining("ivan@example.com");

        verify(userRepository, never()).save(any());
        verify(outboxHelper, never()).enqueue(anyString(), anyString(), anyString(), any());
    }

    // ── 3. Пароль хешируется ─────────────────────────────────────────────────

    @Test
    @DisplayName("3. Пароль хешируется через PasswordEncoder перед сохранением")
    void registerUser_validRequest_passwordIsEncoded() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password1@")).thenReturn("$2a$hashed");

        User saved = new User();
        saved.setId(1L);
        saved.setEmail("ivan@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        registrationService.registerUser(validRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$hashed");
    }

    // ── 4. Поля User заполнены корректно ─────────────────────────────────────

    @Test
    @DisplayName("4. User создаётся с role=ROLE_USER и status=PENDING_VERIFICATION")
    void registerUser_validRequest_userHasCorrectRoleAndStatus() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        User saved = new User();
        saved.setId(1L);
        saved.setEmail("ivan@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        registrationService.registerUser(validRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User toBeSaved = captor.getValue();
        assertThat(toBeSaved.getRole()).isEqualTo("ROLE_USER");
        assertThat(toBeSaved.getStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(toBeSaved.getEmail()).isEqualTo("ivan@example.com");
        assertThat(toBeSaved.getFullName()).isEqualTo("Иван Иванов");
        assertThat(toBeSaved.getPhoneNumber()).isEqualTo("+79161234567");
    }

    // ── 5. Outbox payload содержит userId из сохранённой entity ──────────────

    @Test
    @DisplayName("5. Outbox partitionKey = ID сохранённого User")
    void registerUser_validRequest_outboxPartitionKeyIsUserId() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        User saved = new User();
        saved.setId(99L);
        saved.setEmail("ivan@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        registrationService.registerUser(validRequest);

        verify(outboxHelper).enqueue(
                eq("auth.user.registered"),
                eq("99"),           // partitionKey = userId.toString()
                eq("UserRegistered"),
                any()
        );
    }
}
