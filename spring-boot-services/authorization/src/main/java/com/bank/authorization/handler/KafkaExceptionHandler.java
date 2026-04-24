package com.bank.authorization.handler;

import com.bank.authorization.dto.ErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

/**
 * Публикует ошибки Kafka-обработчиков в топик {@code error.logs}
 * в формате {@link ErrorEvent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaExceptionHandler {

    private static final String SVC = "authorization-service";

    private final KafkaTemplate<String, ErrorEvent> kafkaTemplate;

    @Value("${topics.error-logging}")
    private String errorLoggingTopic;

    /**
     * Определяет код ошибки по типу исключения и публикует {@link ErrorEvent}.
     *
     * @param exception исключение
     * @param requestId идентификатор Kafka-запроса для трассировки
     */
    public void handleException(Exception exception, String requestId) {
        ErrorEvent event = ErrorEvent.builder(SVC, toErrorCode(exception), toMessage(exception))
                .requestId(requestId)
                .build();

        log.error("Kafka exception caught: code={} requestId={} msg={}",
                  event.getErrorCode(), requestId, event.getMessage(), exception);

        kafkaTemplate.send(errorLoggingTopic, event);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String toErrorCode(Exception e) {
        if (e instanceof ValidationException)    return ErrorEvent.CODE_VALIDATION_ERROR;
        if (e instanceof EntityNotFoundException) return ErrorEvent.CODE_NOT_FOUND;
        if (e instanceof BadCredentialsException) return ErrorEvent.CODE_UNAUTHORIZED;
        if (e instanceof SecurityException)       return ErrorEvent.CODE_FORBIDDEN;
        if (e instanceof DataAccessException)     return ErrorEvent.CODE_INTERNAL_ERROR;
        return ErrorEvent.CODE_INTERNAL_ERROR;
    }

    private String toMessage(Exception e) {
        if (e instanceof ValidationException)     return "Validation failed: " + e.getMessage();
        if (e instanceof EntityNotFoundException) return "Entity not found: " + e.getMessage();
        if (e instanceof BadCredentialsException) return "Invalid credentials";
        if (e instanceof SecurityException)       return "Security violation: " + e.getMessage();
        if (e instanceof DataAccessException)     return "Database error: " + e.getMessage();
        return "Unexpected error: " + e.getMessage();
    }
}
