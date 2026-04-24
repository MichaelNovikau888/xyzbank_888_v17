package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.service.ATMService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/public-info/atms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ATMs", description = "Банкоматы")
public class ATMResource {

    @Inject ATMService service;

    @GET
    public List<ATMDto> getByBranch(@QueryParam("branchId") Long branchId) {
        return service.getByBranch(branchId);
    }

    @GET @Path("/{id}")
    public ATMDto getById(@PathParam("id") Long id) {
        return service.getById(id);
    }

    @POST
    public Response create(@Valid ATMDto dto) {
        return Response.status(201).entity(service.create(dto)).build();
    }

    @PUT @Path("/{id}")
    public ATMDto update(@PathParam("id") Long id, @Valid ATMDto dto) {
        dto.setId(id);
        return service.update(dto);
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id);
        return Response.noContent().build();
    }
}
