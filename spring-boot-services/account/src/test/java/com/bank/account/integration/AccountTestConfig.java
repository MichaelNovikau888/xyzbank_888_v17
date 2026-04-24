package com.bank.account.integration;

import com.bank.account.security.TokenValidationService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Тестовая конфигурация: заменяет TokenValidationService заглушкой.
 /
 * Причина: validateJwtOrThrow делает Kafka round-trip к authorization-service,
 * которого нет в интеграционном тесте. Заглушка позволяет тестировать
 * account-specific логику (outbox, idempotency, metrics) изолированно.
/
 * В production-среде реальный TokenValidationService работает как обычно.
 */
@TestConfiguration
public class AccountTestConfig {

    @Bean
    @Primary
    public TokenValidationService tokenValidationService() {
        // Возвращаем mock: validateJwtOrThrow() ничего не делает (no-op)
        TokenValidationService mock = Mockito.mock(TokenValidationService.class);
        Mockito.doNothing().when(mock).validateJwtOrThrow(Mockito.anyString());
        return mock;
    }
}
