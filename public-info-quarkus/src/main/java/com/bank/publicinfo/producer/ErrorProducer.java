package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.ErrorEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Продюсер ошибок в топик {@code error.logs}.
 * Шлёт единый {@link ErrorEvent} — все сервисы используют одинаковую схему.
 */
@ApplicationScoped
public class ErrorProducer {

    private static final Logger LOG = Logger.getLogger(ErrorProducer.class);
    static final         String SVC = "public-info";

    @Inject
    @Channel("error-out")
    Emitter<ErrorEvent> emitter;

    public void sendError(ErrorEvent event) {
        LOG.warnf("Sending error event: service=%s code=%s message=%s",
                  event.getServiceName(), event.getErrorCode(), event.getMessage());
        emitter.send(event);
    }

    public void sendNotFound(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_NOT_FOUND, message)
                .httpStatus(404).build());
    }

    public void sendConflict(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_CONFLICT, message)
                .httpStatus(409).build());
    }

    public void sendValidationError(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_VALIDATION_ERROR, message)
                .httpStatus(400).build());
    }

    public void sendInternalError(String message) {
        sendError(ErrorEvent.builder(SVC, ErrorEvent.CODE_INTERNAL_ERROR, message)
                .httpStatus(500).build());
    }
}
