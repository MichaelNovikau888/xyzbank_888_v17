package com.bank.authorization.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;
import com.bank.authorization.repository.UserRepository;

/**
 * Бизнес-метрики authorization-service.
/
 * Ключевые алерты:
 *   CRITICAL: auth_login_failed_total rate > 100/min (brute-force атака)
 *   CRITICAL: auth_token_invalid_total rate > 50/min (компрометация токенов?)
 *   WARNING:  auth_user_created_total rate == 0 за час (регистрация сломана)
 */
@Component
@Getter
public class AuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailed;
    private final Counter tokenValid;
    private final Counter tokenInvalid;

    private final Counter userCreated;
    private final Counter userUpdated;
    private final Counter userDeleted;
    private final Counter userIdempotentSkipped;

    public AuthMetrics(MeterRegistry registry, UserRepository userRepository) {

        this.loginSuccess = Counter.builder("auth_login_success_total")
                .description("Successful login attempts").register(registry);
        this.loginFailed = Counter.builder("auth_login_failed_total")
                .description("Failed login attempts (wrong credentials)")
                .register(registry);
        this.tokenValid = Counter.builder("auth_token_valid_total")
                .description("JWT tokens successfully validated").register(registry);
        this.tokenInvalid = Counter.builder("auth_token_invalid_total")
                .description("JWT token validation failures").register(registry);

        this.userCreated = Counter.builder("auth_user_created_total")
                .description("Users created via Kafka command").register(registry);
        this.userUpdated = Counter.builder("auth_user_updated_total")
                .description("Users updated via Kafka command").register(registry);
        this.userDeleted = Counter.builder("auth_user_deleted_total")
                .description("Users deleted via Kafka command").register(registry);
        this.userIdempotentSkipped = Counter.builder("auth_user_idempotent_skipped_total")
                .description("Duplicate user commands skipped by idempotency guard").register(registry);

        Gauge.builder("auth_users_total", userRepository,
                        repo -> (double) repo.count())
                .description("Total users registered in the system").register(registry);
    }
}
