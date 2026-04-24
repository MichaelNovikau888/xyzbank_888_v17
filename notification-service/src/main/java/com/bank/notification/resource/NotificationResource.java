package com.bank.notification.resource;

import com.bank.notification.dto.ErrorResponse;
import com.bank.notification.entity.NotificationRecord;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API для просмотра истории уведомлений клиента.
 */
 /**
 * GET /api/v1/notifications/{clientId}?page=0&size=20
 */
 /**
 * Возвращает только финальные статусы (COMPLETED, FAILED, CANCELLED, BLOCKED),
 * отсортированные по дате уведомления (новые первые).
 */
@Path("/api/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    private static final Logger LOG = Logger.getLogger(NotificationResource.class);

    @GET
    @Path("/{clientId}")
    public Response getNotifications(
            @PathParam("clientId") String clientId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        if (clientId == null || clientId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("clientId must not be blank"))
                           .build();
        }
        if (size < 1 || size > 100) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("size must be between 1 and 100"))
                           .build();
        }

        List<NotificationRecord> records =
                NotificationRecord.findByClientIdPaged(clientId, page, size);
        long total = NotificationRecord.countByClientId(clientId);

        LOG.debugf("Fetched %d notification records for client_id=%s (page=%d, size=%d)",
                   records.size(), clientId, page, size);

        List<NotificationRecordDto> dtos = records.stream()
                .map(NotificationRecordDto::from)
                .toList();

        return Response.ok(new PagedResponse<>(dtos, total, page, size)).build();
    }

    // ── DTOs (внутренние — специфичны только для этого эндпоинта) ────────────

    public static class NotificationRecordDto {
        public Long          id;
        public Long          paymentId;
        public String        finalStatus;
        public BigDecimal    amount;
        public String        currency;
        public String        recipientAccount;
        public String        reason;
        public LocalDateTime notifiedAt;

        public static NotificationRecordDto from(NotificationRecord r) {
            NotificationRecordDto dto = new NotificationRecordDto();
            dto.id               = r.getId();
            dto.paymentId        = r.getPaymentId();
            dto.finalStatus      = r.getFinalStatus();
            dto.amount           = r.getAmount();
            dto.currency         = r.getCurrency();
            dto.recipientAccount = r.getRecipientAccount();
            dto.reason           = r.getReason();
            dto.notifiedAt       = r.getNotifiedAt();
            return dto;
        }
    }

    public static class PagedResponse<T> {
        public List<T> content;
        public long    total;
        public int     page;
        public int     size;
        public int     totalPages;

        public PagedResponse(List<T> content, long total, int page, int size) {
            this.content    = content;
            this.total      = total;
            this.page       = page;
            this.size       = size;
            this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        }
    }
}
