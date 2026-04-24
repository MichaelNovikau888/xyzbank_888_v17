package com.bank.profile.handler;

import com.bank.profile.dto.ErrorEvent;
import com.bank.profile.exception.EntityNotUniqueException;
import com.bank.profile.kafka.producer.ErrorProducer;
import jakarta.inject.Inject;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Глобальный обработчик исключений для JAX-RS ресурсов.
 * Все ошибки шлются в топик error.logs в формате {@link ErrorEvent}.
 */
public class GlobalExceptionHandler {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class);

    // ── EntityNotFoundException → 404 ────────────────────────────────────────

    @Provider
    public static class NotFoundMapper implements ExceptionMapper<EntityNotFoundException> {

        @Inject ErrorProducer errorProducer;

        @Override
        public Response toResponse(EntityNotFoundException ex) {
            LOG.warnf("Entity not found: %s", ex.getMessage());
            errorProducer.sendNotFound(ex.getMessage());
            return errorResponse(Response.Status.NOT_FOUND, ex.getMessage());
        }
    }

    // ── EntityNotUniqueException → 409 ───────────────────────────────────────

    @Provider
    public static class NotUniqueMapper implements ExceptionMapper<EntityNotUniqueException> {

        @Inject ErrorProducer errorProducer;

        @Override
        public Response toResponse(EntityNotUniqueException ex) {
            String msg = ex.className + " with the same " + ex.fieldName + " already exists";
            LOG.warnf("Entity not unique: %s", msg);
            errorProducer.sendConflict(msg);
            return errorResponse(Response.Status.CONFLICT, msg);
        }
    }

    // ── EntityExistsException → 409 ──────────────────────────────────────────

    @Provider
    public static class ExistsMapper implements ExceptionMapper<EntityExistsException> {

        @Inject ErrorProducer errorProducer;

        @Override
        public Response toResponse(EntityExistsException ex) {
            LOG.warnf("Entity already exists: %s", ex.getMessage());
            errorProducer.sendConflict(ex.getMessage());
            return errorResponse(Response.Status.CONFLICT, ex.getMessage());
        }
    }

    // ── Generic fallback → 500 ────────────────────────────────────────────────

    @Provider
    public static class GenericMapper implements ExceptionMapper<Exception> {

        @Inject ErrorProducer errorProducer;

        @Override
        public Response toResponse(Exception ex) {
            LOG.errorf(ex, "Unexpected error: %s", ex.getMessage());
            errorProducer.sendInternalError("Internal server error: " + ex.getMessage());
            return errorResponse(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static Response errorResponse(Response.Status status, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"" + message + "\"}")
                .build();
    }
}
