package com.bank.account.exception;

import com.bank.account.exception.error_dto.ErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Публикует ошибки в топик {@code error.logs} в формате {@link ErrorEvent}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaErrorSender {

    private static final String SVC = "account-service";

    private final KafkaTemplate<String, ErrorEvent> kafkaTemplate;
    private final GlobalExceptionHandler            globalExceptionHandler;

    /**
     * Публикует ошибку в указанный топик.
     * Тип ошибки определяется через {@link GlobalExceptionHandler#toErrorCode(Exception)}.
     *
     * @param e        исключение
     * @param topic    имя топика (обычно {@code error.logs})
     * @param requestId идентификатор запроса (может быть null)
     */
    public void sendError(Exception e, String topic, String requestId) {
        try {
            ErrorEvent event = ErrorEvent.builder(SVC,
                            globalExceptionHandler.toErrorCode(e),
                            e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    .requestId(requestId)
                    .build();
            kafkaTemplate.send(topic, event);
            log.error("Error sent to Kafka topic '{}': code={} msg={}",
                      topic, event.getErrorCode(), event.getMessage(), e);
        } catch (Exception ex) {
            log.error("Failed to send error to Kafka", ex);
        }
    }

    /** Перегрузка без requestId для обратной совместимости. */
    public void sendError(Exception e, String topic) {
        sendError(e, topic, null);
    }
}
