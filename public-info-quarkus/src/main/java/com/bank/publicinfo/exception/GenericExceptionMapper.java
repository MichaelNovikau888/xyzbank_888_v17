package com.bank.publicinfo.exception;

import com.bank.publicinfo.dto.ErrorResponseDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);
    @Override
    public Response toResponse(Exception ex) {
        LOG.errorf(ex, "Unexpected error: %s", ex.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponseDto("INTERNAL_ERROR", "Internal server error: " + ex.getMessage()))
                .build();
    }
}
