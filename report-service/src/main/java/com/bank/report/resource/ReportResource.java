package com.bank.report.resource;

import com.bank.report.dto.PaymentReportDto;
import com.bank.report.dto.PeriodSummaryDto;
import com.bank.report.dto.TransferReportDto;
import com.bank.report.security.JwtUtil;
import com.bank.report.service.ReportService;
import io.jsonwebtoken.JwtException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation; //Cannot resolve symbol 'Operation' --red flag
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType; //Cannot resolve symbol 'SecuritySchemeType'--red flag
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;//Cannot resolve symbol 'SecurityRequirement'--red flag
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;//Cannot resolve symbol 'SecurityScheme'--red flag
import org.eclipse.microprofile.openapi.annotations.tags.Tag;//Cannot resolve symbol 'Tag'--red flag
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API отчётности с JWT-авторизацией.
 */
/**
 * Две роли доступа:
 * /client/{clientId}/... — клиент видит только свои данные.
 * JWT-токен обязателен; clientId из токена должен совпадать
 * с clientId из пути. Исключение: ROLE_ADMIN может
 * просматривать данные любого клиента.
 * /bank/...               — бухгалтерия. Требует ROLE_ADMIN.
 */

/**
 * Тест-профиль: app.jwt.auth-enabled=false отключает проверки,
 * что позволяет запускать интеграционные тесты без реальных токенов.
 */
@Path("/api/v1/reports")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Отчётность по платежам и переводам")//Cannot resolve symbol 'Tag'--red flag
@SecurityScheme(securitySchemeName = "BearerAuth", //Cannot resolve symbol 'SecurityScheme'--red flag
        type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
//Cannot resolve symbol 'SecuritySchemeType'--red flag
public class ReportResource {

    @Inject
    ReportService reportService;
    @Inject
    JwtUtil jwtUtil;

    @ConfigProperty(name = "app.jwt.auth-enabled", defaultValue = "true")
    boolean authEnabled;

    // ═══════════════════════════════════════════════════════════════
    // КЛИЕНТСКИЙ ВИД: только свои данные
    // ═══════════════════════════════════════════════════════════════

    @GET
    @Path("/client/{clientId}/payments/day")
    @Operation(summary = "Платежи клиента за день")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'----red flag
    public List<PaymentReportDto> clientPaymentsByDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("clientId") Long clientId,
            @RestQuery LocalDate date) {
        checkClientAccess(auth, clientId);
        return reportService.getClientPaymentsByDay(clientId,
                date != null ? date : LocalDate.now());
    }

    @GET
    @Path("/client/{clientId}/transfers/day")
    @Operation(summary = "Переводы клиента за день")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public List<TransferReportDto> clientTransfersByDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("clientId") Long clientId,
            @RestQuery LocalDate date) {
        checkClientAccess(auth, clientId);
        return reportService.getClientTransfersByDay(clientId,
                date != null ? date : LocalDate.now());
    }

    @GET
    @Path("/client/{clientId}/summary/day")
    @Operation(summary = "Сводка клиента за день (платежи + переводы)") //Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto clientSummaryDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("clientId") Long clientId,
            @RestQuery LocalDate date) {
        checkClientAccess(auth, clientId);
        LocalDate d = date != null ? date : LocalDate.now();
        return reportService.getClientSummary(clientId, d, d, d.toString());
    }

    @GET
    @Path("/client/{clientId}/summary/week")
    @Operation(summary = "Сводка клиента за неделю")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto clientSummaryWeek(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("clientId") Long clientId,
            @RestQuery LocalDate date) {
        checkClientAccess(auth, clientId);
        LocalDate d = date != null ? date : LocalDate.now();
        LocalDate[] range = ReportService.weekRange(d);
        String label = d.getYear() + "-W"
                + String.format("%02d", d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
        return reportService.getClientSummary(clientId, range[0], range[1], label);
    }

    @GET
    @Path("/client/{clientId}/summary/month")
    @Operation(summary = "Сводка клиента за месяц")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto clientSummaryMonth(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("clientId") Long clientId,
            @RestQuery LocalDate date) {
        checkClientAccess(auth, clientId);
        LocalDate d = date != null ? date : LocalDate.now();
        LocalDate[] range = ReportService.monthRange(d);
        String label = d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        return reportService.getClientSummary(clientId, range[0], range[1], label);
    }

    // ═══════════════════════════════════════════════════════════════
    // БУХГАЛТЕРСКИЙ ВИД: все данные банка (требует ROLE_ADMIN)
    // ═══════════════════════════════════════════════════════════════

    @GET
    @Path("/bank/payments/day")
    @Operation(summary = "Все платежи за день (бухгалтерия)")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public List<PaymentReportDto> bankPaymentsByDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        return reportService.getAllPaymentsByDay(date != null ? date : LocalDate.now());
    }

    @GET
    @Path("/bank/transfers/day")
    @Operation(summary = "Все переводы за день (бухгалтерия)")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public List<TransferReportDto> bankTransfersByDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        return reportService.getAllTransfersByDay(date != null ? date : LocalDate.now());
    }

    @GET
    @Path("/bank/summary/day")
    @Operation(summary = "Сводка по банку за день")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto bankSummaryDay(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        LocalDate d = date != null ? date : LocalDate.now();
        return reportService.getBankSummary(d, d, d.toString());
    }

    @GET
    @Path("/bank/summary/week")
    @Operation(summary = "Сводка по банку за неделю")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto bankSummaryWeek(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        LocalDate d = date != null ? date : LocalDate.now();
        LocalDate[] r = ReportService.weekRange(d);
        String label = d.getYear() + "-W"
                + String.format("%02d", d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
        return reportService.getBankSummary(r[0], r[1], label);
    }

    @GET
    @Path("/bank/summary/month")
    @Operation(summary = "Сводка по банку за месяц")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public PeriodSummaryDto bankSummaryMonth(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        LocalDate d = date != null ? date : LocalDate.now();
        LocalDate[] r = ReportService.monthRange(d);
        String label = d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        return reportService.getBankSummary(r[0], r[1], label);
    }

    @GET
    @Path("/bank/daily")
    @Produces("text/csv")
    @Operation(summary = "CSV-отчёт за день (бухгалтерия)")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public Response generateDailyCsv(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @RestQuery LocalDate date) {
        checkAdminAccess(auth);
        LocalDate d = date != null ? date : LocalDate.now();
        byte[] csv = reportService.generateDailyReportCsv(d);
        String fname = "report_" + d + ".csv";
        return Response.ok(csv)
                .header("Content-Disposition", "attachment; filename=\"" + fname + "\"")
                .build();
    }

    @GET
    @Path("/bank/partitions/health")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Здоровье партиций PostgreSQL")//Cannot resolve symbol 'Operation'--red flag
    @SecurityRequirement(name = "BearerAuth")//Cannot resolve symbol 'SecurityRequirement'--red flag
    public Response partitionHealth(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth) {
        checkAdminAccess(auth);
        return Response.ok(reportService.checkPartitionHealth()).build();
    }

    // ═══════════════════════════════════════════════════════════════
    // Private: JWT-проверки
    // ═══════════════════════════════════════════════════════════════

    /**
     * Проверяет доступ к данным клиента.
     * ROLE_ADMIN — любой clientId; остальные — только свой clientId.
     *
     * @throws WebApplicationException 401 если токен невалиден; 403 если clientId чужой
     */
    private void checkClientAccess(String auth, Long pathClientId) {
        if (!authEnabled) return;
        requireBearer(auth);
        try {
            if (jwtUtil.hasRole(auth, "ROLE_ADMIN")) return;
            Long tokenClientId = jwtUtil.extractClientId(auth);
            if (!tokenClientId.equals(pathClientId)) {
                throw new WebApplicationException(
                        Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"error\":\"Access denied: clientId mismatch\"}")
                                .type(MediaType.APPLICATION_JSON)
                                .build());
            }
        } catch (WebApplicationException e) {
            throw e;
        } catch (JwtException e) {
            throw unauthorized("Invalid or expired JWT: " + e.getMessage());
        }
    }

    /**
     * Проверяет наличие роли ROLE_ADMIN.
     *
     * @throws WebApplicationException 401 если токен невалиден; 403 если роль не ROLE_ADMIN
     */
    private void checkAdminAccess(String auth) {
        if (!authEnabled) return;
        requireBearer(auth);
        try {
            if (!jwtUtil.hasRole(auth, "ROLE_ADMIN")) {
                throw new WebApplicationException(
                        Response.status(Response.Status.FORBIDDEN)
                                .entity("{\"error\":\"Access denied: ROLE_ADMIN required\"}")
                                .type(MediaType.APPLICATION_JSON)
                                .build());
            }
        } catch (WebApplicationException e) {
            throw e;
        } catch (JwtException e) {
            throw unauthorized("Invalid or expired JWT: " + e.getMessage());
        }
    }

    private void requireBearer(String auth) {
        if (!jwtUtil.hasBearerToken(auth)) {
            throw unauthorized("Authorization header with Bearer token is required");
        }
    }

    private WebApplicationException unauthorized(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"error\":\"" + message + "\"}")
                        .type(MediaType.APPLICATION_JSON)
                        .build());
    }
}
