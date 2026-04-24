package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.BranchDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.service.BranchService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/public-info/branches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Branches", description = "Отделения банка")
public class BranchResource {

    @Inject BranchService service;

    @GET
    public PagedResponse<BranchDto> getAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return service.getAll(page, size);
    }

    @GET @Path("/{id}")
    public BranchDto getById(@PathParam("id") Long id) {
        return service.getById(id);
    }

    @POST
    public Response create(@Valid BranchDto dto) {
        return Response.status(201).entity(service.create(dto)).build();
    }

    @PUT @Path("/{id}")
    public BranchDto update(@PathParam("id") Long id, @Valid BranchDto dto) {
        return service.update(id, dto);
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id);
        return Response.noContent().build();
    }
}
