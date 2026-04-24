package com.bank.authorization.controller;

import com.bank.authorization.integration.AbstractIntegrationTest;
import com.bank.authorization.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест POST /api/v1/auth/register.
 */
 /**
 * <p>Использует полный Spring-контекст (Testcontainers PostgreSQL + Kafka)
 * через {@link AbstractIntegrationTest}. MockMvc позволяет тестировать HTTP-слой
 * без поднятия реального сетевого порта.
 */
 /**
 * <p>Покрываемые сценарии:
 * <ol>
 *   <li>Валидные данные → 201 Created</li>
 *   <li>Дублирующий email → 409 Conflict</li>
 *   <li>Невалидный email → 400 Bad Request</li>
 *   <li>Слабый пароль (без спецсимвола) → 400 Bad Request</li>
 *   <li>Короткий пароль (&lt; 8 символов) → 400 Bad Request</li>
 * </ol>
 */
@AutoConfigureMockMvc
@DisplayName("AuthController — POST /api/v1/auth/register")
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @AfterEach
    void cleanDbAfter() {
        userRepository.deleteAll();
    }

    // ── 1. Успешная регистрация ───────────────────────────────────────────────

    @Test
    @DisplayName("1. Валидные данные → 201 Created, userId и email в ответе")
    void register_validRequest_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",       "ivan@example.com",
                "password",    "Strong1@pass",
                "fullName",    "Иван Иванов",
                "phoneNumber", "+79161234567"
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.requiresEmailVerification").value(true));
    }

    // ── 2. Дублирующий email → 409 ────────────────────────────────────────────

    @Test
    @DisplayName("2. Дублирующий email → 409 Conflict")
    void register_duplicateEmail_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",       "dup@example.com",
                "password",    "Strong1@pass",
                "fullName",    "Дубль Дублевич",
                "phoneNumber", "+79001112233"
        ));

        // Первая регистрация — успешно
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Вторая с тем же email → 409
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("dup@example.com"))
                .andExpect(jsonPath("$.requiresEmailVerification").value(false));
    }

    // ── 3. Невалидный email → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("3. Невалидный email → 400 Bad Request")
    void register_invalidEmail_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",       "not-an-email",
                "password",    "Strong1@pass",
                "fullName",    "Тест Тестов",
                "phoneNumber", "+79001234567"
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 4. Пароль без спецсимвола → 400 ──────────────────────────────────────

    @Test
    @DisplayName("4. Пароль без спецсимвола → 400 Bad Request")
    void register_passwordWithoutSpecialChar_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",       "user@example.com",
                "password",    "NoSpecial1",   // нет @$!%*?&
                "fullName",    "Тест Тестов",
                "phoneNumber", "+79001234567"
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── 5. Слишком короткий пароль → 400 ─────────────────────────────────────

    @Test
    @DisplayName("5. Пароль короче 8 символов → 400 Bad Request")
    void register_shortPassword_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email",       "user2@example.com",
                "password",    "Ab1@",          // 4 символа — меньше минимума
                "fullName",    "Тест Тестов",
                "phoneNumber", "+79001234567"
        ));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
