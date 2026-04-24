package com.bank.publicinfo.exception;

import com.bank.publicinfo.dto.ErrorResponseDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class EntityNotFoundExceptionMapper implements ExceptionMapper<EntityNotFoundException> {
    private static final Logger LOG = Logger.getLogger(EntityNotFoundExceptionMapper.class);
    @Override
    public Response toResponse(EntityNotFoundException ex) {
        LOG.warnf("Entity not found: %s", ex.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponseDto("ENTITY_NOT_FOUND", ex.getMessage()))
                .build();
    }
}
