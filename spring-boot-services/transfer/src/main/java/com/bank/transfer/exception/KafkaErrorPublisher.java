package com.bank.transfer.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

/**
 * Публикует ошибки в Kafka-топик {@code error.logs} в формате {@link ErrorEvent}.
 * Используется Kafka-консьюмерами и сервисами для централизованной
 * отправки информации об ошибках.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaErrorPublisher {

    private static final String SVC = "transfer-service";

    private final KafkaTemplate<String, ErrorEvent> kafkaTemplate;

    /**
     * Публикует ошибку с requestId.
     *
     * @param exception исключение
     * @param requestId идентификатор запроса/транзакции
     */
    public void publish(Exception exception, String requestId) {
        ErrorEvent event = ErrorEvent.builder(SVC, toErrorCode(exception), toMessage(exception))
                .httpStatus(toHttpStatus(exception))
                .requestId(requestId)
                .build();

        log.error("Publishing error to Kafka: code={} requestId={} msg={}",
                  event.getErrorCode(), requestId, event.getMessage(), exception);

        kafkaTemplate.send("error.logs", event);
    }

    /** Перегрузка без requestId. */
    public void publish(Exception exception) {
        publish(exception, null);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private String toErrorCode(Exception e) {
        if (e instanceof EntityNotFoundException)        return ErrorEvent.CODE_NOT_FOUND;
        if (e instanceof ValidationException)            return ErrorEvent.CODE_VALIDATION_ERROR;
        if (e instanceof IllegalArgumentException)       return ErrorEvent.CODE_VALIDATION_ERROR;
        if (e instanceof DataIntegrityViolationException) return ErrorEvent.CODE_CONFLICT;
        return ErrorEvent.CODE_INTERNAL_ERROR;
    }

    private String toMessage(Exception e) {
        if (e instanceof EntityNotFoundException)
            return "The requested resource was not found: " + e.getMessage();
        if (e instanceof ValidationException)
            return "Invalid input: " + e.getMessage();
        if (e instanceof IllegalArgumentException)
            return "Invalid argument: " + e.getMessage();
        if (e instanceof DataIntegrityViolationException)
            return "Database constraint violated: " + e.getMessage();
        return "Unexpected error: " + e.getMessage();
    }

    private int toHttpStatus(Exception e) {
        if (e instanceof EntityNotFoundException)        return 404;
        if (e instanceof ValidationException)            return 400;
        if (e instanceof IllegalArgumentException)       return 400;
        if (e instanceof DataIntegrityViolationException) return 409;
        return 500;
    }
}
