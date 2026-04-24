package com.bank.authorization.integration;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Тестовая конфигурация authorization-service.
 /
 * Authorization-service содержит собственный JwtTokenUtil и AuthenticationManager —
 * они тестируются напрямую без заглушек. Этот класс служит точкой расширения
 * для будущих test-only бинов (например, mock внешних клиентов).
 */
@TestConfiguration
public class AuthTestConfig {
    // no-op: все нужные бины поднимаются из основного контекста
}
