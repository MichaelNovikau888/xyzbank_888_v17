package com.bank.publicinfo.exception;

import com.bank.publicinfo.dto.ErrorResponseDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    private static final Logger LOG = Logger.getLogger(IllegalArgumentExceptionMapper.class);
    @Override
    public Response toResponse(IllegalArgumentException ex) {
        LOG.warnf("Illegal argument: %s", ex.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponseDto("ILLEGAL_ARGUMENT", ex.getMessage()))
                .build();
    }
}
