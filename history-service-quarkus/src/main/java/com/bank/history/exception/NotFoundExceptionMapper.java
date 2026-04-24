package com.bank.history.exception;

import com.bank.history.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** EntityNotFoundException → 404. Аналог Spring @ExceptionHandler(EntityNotFoundException.class) */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<EntityNotFoundException> {

    private static final Logger log = Logger.getLogger(NotFoundExceptionMapper.class);

    @Override
    public Response toResponse(EntityNotFoundException ex) {
        log.error("Entity not found", ex);
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ex.getMessage(), 404))
                .build();
    }
}
