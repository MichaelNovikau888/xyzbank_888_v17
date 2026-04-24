package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.CertificateDto;
import com.bank.publicinfo.service.CertificateService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/public-info/certificates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Certificates", description = "Сертификаты банка")
public class CertificateResource {

    @Inject CertificateService service;

    @GET
    public List<CertificateDto> getByBank(@QueryParam("bankDetailsId") Long bankDetailsId) {
        return service.getByBankDetails(bankDetailsId);
    }

    @GET @Path("/{id}")
    public CertificateDto getById(@PathParam("id") Long id) { return service.getById(id); }

    @POST
    public Response create(CertificateDto dto) {
        return Response.status(201).entity(service.create(dto)).build();
    }

    @PUT @Path("/{id}")
    public CertificateDto update(@PathParam("id") Long id, CertificateDto dto) {
        dto.setId(id); return service.update(dto);
    }

    @DELETE @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        service.deleteById(id); return Response.noContent().build();
    }
}
