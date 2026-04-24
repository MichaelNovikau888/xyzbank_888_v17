package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.LicenseDto;
import com.bank.publicinfo.service.LicenseService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/public-info/licenses")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Licenses", description = "Лицензии банка")
public class LicenseResource {

    @Inject LicenseService service;

    @GET
    public List<LicenseDto> getByBank(@QueryParam("bankDetailsId") Long bankDetailsId) {
        return service.getByBankDetails(bankDetailsId);
    }

    @GET @Path("/{id}")
    public LicenseDto getById(@PathParam("id") Long id) { return service.getById(id); }

    @POST
    public Response create(LicenseDto dto) {
        return Response.status(201).entity(service.create(dto)).build();
    }

    @PUT @Path("/{id}")
    public LicenseDto update(@PathParam("id") Long id, LicenseDto dto) {
        dto.setId(id); return service.update(dto);
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id); return Response.noContent().build();
    }
}
