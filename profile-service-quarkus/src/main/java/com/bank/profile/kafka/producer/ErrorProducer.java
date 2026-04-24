package com.bank.profile.kafka.producer;

import com.bank.profile.dto.ErrorEvent;
import com.bank.profile.util.KafkaTopic;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер ошибок в топик {@code error.logs}.
 * Шлёт единый {@link ErrorEvent} — все сервисы используют одинаковую схему.
 */
@ApplicationScoped
public class ErrorProducer {

    private static final Logger LOG    = Logger.getLogger(ErrorProducer.class);
    static final         String SVC    = "profile-service";

    @Inject
    @Channel("error-out")
    Emitter<ErrorEvent> errorEmitter;

    @Inject
    KafkaTopic topicsConfig;

    /**
     * Шлёт {@link ErrorEvent} в топик {@code error.logs}.
     *
     * @param event готовое событие ошибки
     */
    public void sendError(ErrorEvent event) {
        LOG.warnf("Sending error event: service=%s code=%s message=%s",
                  event.getServiceName(), event.getErrorCode(), event.getMessage());
        errorEmitter.send(
                Message.of(event).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withTopic(topicsConfig.getTopicError())
                                .build()
                )
        );
    }

    // ── Фабричные методы для типовых ошибок ──────────────────────────────────

    /** 404 — сущность не найдена. */
    public void sendNotFound(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_NOT_FOUND, message)
                .httpStatus(404).build());
    }

    /** 409 — конфликт (дубликат). */
    public void sendConflict(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_CONFLICT, message)
                .httpStatus(409).build());
    }

    /** 500 — внутренняя ошибка. */
    public void sendInternalError(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_INTERNAL_ERROR, message)
                .httpStatus(500).build());
    }
}
