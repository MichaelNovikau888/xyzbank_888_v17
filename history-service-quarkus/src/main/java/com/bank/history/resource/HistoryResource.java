package com.bank.history.resource;

import com.bank.history.dto.HistoryDto;
import com.bank.history.dto.PagedResponse;
import com.bank.history.service.HistoryService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.executable.ExecutableType;
import jakarta.validation.executable.ValidateOnExecution;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST-ресурс истории событий.
 * /
 * /**
 * Эндпоинты:
 * GET /api/history                          — все события (пагинация)
 * GET /api/history/service/{name}           — по имени сервиса (пагинация)
 * GET /api/history/type/{eventType}         — по типу события (пагинация)
 * GET /api/history/transfer/{id}            — по ID аудита перевода (пагинация)
 * GET /api/history/recent?limit=N           — последние N событий (без пагинации)
 */
@Path("/api/history")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "History", description = "История всех операций системы")
@ValidateOnExecution(type = ExecutableType.ALL)
public class HistoryResource {

    @Inject
    HistoryService historyService;

    @GET
    @Operation(
            summary = "Все события (с пагинацией)",
            description = "Возвращает историю событий постранично, отсортированную от новых к старым"
    )
    public PagedResponse<HistoryDto> getAll(
            @Parameter(description = "Номер страницы (0-based)")
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Размер страницы (1–200)")
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {
        return historyService.getAuditHistory(page, size);
    }

    @GET
    @Path("/service/{name}")
    @Operation(summary = "События по имени сервиса (с пагинацией)")
    public PagedResponse<HistoryDto> getByService(
            @PathParam("name") String name,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {
        return historyService.getAuditHistoryByServiceName(name, page, size);
    }

    @GET
    @Path("/type/{eventType}")
    @Operation(summary = "События по типу (с пагинацией)")
    public PagedResponse<HistoryDto> getByType(
            @PathParam("eventType") String eventType,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {
        return historyService.getAuditHistoryByEventType(eventType, page, size);
    }

    @GET
    @Path("/transfer/{transferAuditId}")
    @Operation(summary = "События по ID аудита трансфера (с пагинацией)")
    public PagedResponse<HistoryDto> getByTransferId(
            @PathParam("transferAuditId") Long transferAuditId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {
        return historyService.getAuditHistoryByTransferId(transferAuditId, page, size);
    }

    /**
     * Последние N событий — удобно для дашбордов и мониторинга.
     * Использует {@link com.bank.history.repository.HistoryRepository#findRecentEvents}.
     */
    /**
     * GET /api/history/recent?limit=10
     */
    @GET
    @Path("/recent")
    @Operation(
            summary = "Последние N событий",
            description = "Возвращает последние N событий без пагинации. Максимум 100."
    )
    public List<HistoryDto> getRecent(
            @Parameter(description = "Количество событий (1–100)")
            @QueryParam("limit") @DefaultValue("10") @Min(1) @Max(100) int limit) {
        return historyService.getRecentEvents(limit);
    }
}
