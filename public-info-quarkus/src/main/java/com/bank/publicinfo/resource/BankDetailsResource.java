package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.service.BankDetailsService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/public-info/bank-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "BankDetails", description = "Банковские реквизиты")
public class BankDetailsResource {

    @Inject BankDetailsService service;

    @GET
    public PagedResponse<BankDetailsDto> getAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return service.getAll(page, size);
    }

    @GET @Path("/{id}")
    public BankDetailsDto getById(@PathParam("id") Long id) {
        return service.getById(id);
    }

    @POST
    public Response create(@Valid BankDetailsDto dto) {
        return Response.status(201).entity(service.create(dto)).build();
    }

    @PUT @Path("/{id}")
    public BankDetailsDto update(@PathParam("id") Long id, @Valid BankDetailsDto dto) {
        dto.setId(id);
        return service.update(dto);
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id);
        return Response.noContent().build();
    }
}
