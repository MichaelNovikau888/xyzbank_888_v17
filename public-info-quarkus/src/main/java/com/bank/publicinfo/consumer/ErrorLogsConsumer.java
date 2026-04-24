package com.bank.publicinfo.consumer;

import com.bank.publicinfo.dto.ErrorEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Консьюмер топика {@code error.logs}.
 *
 * <p>Логирует входящие ошибки от всех сервисов.
 * Теперь десериализует единый {@link ErrorEvent} —
 * все продюсеры шлют одинаковую схему.
 */
@ApplicationScoped
public class ErrorLogsConsumer {

    private static final Logger LOG = Logger.getLogger(ErrorLogsConsumer.class);

    @Inject ObjectMapper objectMapper;

    @Incoming("error-logs") @Blocking
    public void handleErrorLog(String message) {
        try {
            ErrorEvent event = objectMapper.readValue(message, ErrorEvent.class);
            LOG.warnf("Error log received: service=%s code=%s httpStatus=%s msg=%s",
                      event.getServiceName(),
                      event.getErrorCode(),
                      event.getHttpStatus(),
                      event.getMessage());
        } catch (Exception e) {
            // Обратная совместимость: если пришёл raw payload — просто логируем
            LOG.warnf("Error log received (raw): %s", message);
        }
    }
}
