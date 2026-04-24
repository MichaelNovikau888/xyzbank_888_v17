package com.bank.publicinfo.resource;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.service.AuditService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/public-info/audits")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Audit", description = "Аудит операций")
public class AuditResource {

    @Inject AuditService service;

    @GET
    @Operation(summary = "Получить все записи аудита (с пагинацией)")
    public PagedResponse<AuditDto> getAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        return service.getAll(page, size);
    }

    @GET @Path("/{id}")
    @Operation(summary = "Получить запись аудита по ID")
    public AuditDto getById(@PathParam("id") Long id) {
        return service.getById(id);
    }

 /**
     * GET /api/public-info/audits/by-entity-type?type=ATMDto
     * Фильтрация аудита по типу сущности — использует AuditRepository.findByEntityType().
 */
    @GET @Path("/by-entity-type")
    @Operation(summary = "Найти первую запись аудита по типу сущности")
    public AuditDto getByEntityType(@QueryParam("type") String entityType) {
        if (entityType == null || entityType.isBlank())
            throw new IllegalArgumentException("Query param 'type' must not be blank");
        return service.getByEntityType(entityType);
    }

 /**
     * GET /api/public-info/audits/by-entity-json?json=...
     * Все записи аудита, чей entityJson содержит указанный фрагмент — использует findAllByEntityJson().
 */
    @GET @Path("/by-entity-json")
    @Operation(summary = "Найти все записи аудита по фрагменту JSON")
    public List<AuditDto> getAllByEntityJson(@QueryParam("json") String entityJson) {
        if (entityJson == null || entityJson.isBlank())
            throw new IllegalArgumentException("Query param 'json' must not be blank");
        return service.getAllByEntityJson(entityJson);
    }
}
