package com.bank.history.exception;

import com.bank.history.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Catch-all: любое непойманное исключение → 500. */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception ex) {
        log.error("Unexpected error", ex);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("Internal server error: " + ex.getMessage(), 500))
                .build();
    }
}
