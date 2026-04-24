package com.bank.profile.resource;
import com.bank.profile.dto.ProfileDto;
import com.bank.profile.service.ProfileService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;

@Path("/api/profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name="Profiles", description="Профили пользователей банка")
public class ProfileResource {

    @Inject ProfileService profileService;

    @GET
    @Operation(summary = "Получить все профили")
    public List<ProfileDto> getAll() { return profileService.getAll(); }

    @GET @Path("/{id}")
    @Operation(summary = "Получить профиль по ID")
    public ProfileDto getById(@PathParam("id") Long id) { return profileService.get(id); }

    @POST
    @Operation(summary = "Создать профиль")
    public Response create(@Valid ProfileDto dto) {
        return Response.status(Response.Status.CREATED).entity(profileService.create(dto)).build();
    }

    @PUT @Path("/{id}")
    @Operation(summary = "Обновить профиль")
    public ProfileDto update(@PathParam("id") Long id, @Valid ProfileDto dto) {
        return profileService.update(id, dto);
    }

    @DELETE @Path("/{id}")
    @Operation(summary = "Удалить профиль")
    public Response delete(@PathParam("id") Long id) {
        profileService.delete(id);
        return Response.noContent().build();
    }
}
