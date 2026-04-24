package com.bank.notification.resource;

import com.bank.notification.dto.ErrorResponse;
import com.bank.notification.dto.RegisterPushTokenRequest;
import com.bank.notification.dto.RegisterPushTokenResponse;
import com.bank.notification.service.PushNotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API для регистрации push-токенов мобильных устройств.
 */
 /**
 * POST /api/v1/push/register — вызывается из мобильного приложения при первом запуске
 *                               или при обновлении FCM-токена.
 * Токен сохраняется в Redis: push:token:{clientId}, TTL=30 дней.
 */
@Path("/api/v1/push")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PushTokenResource {

    @Inject
    PushNotificationService pushNotificationService;

    @POST
    @Path("/register")
    public Response registerPushToken(RegisterPushTokenRequest request) {
        if (request.clientId == null || request.clientId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("clientId must not be blank"))
                           .build();
        }
        if (request.pushToken == null || request.pushToken.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("pushToken must not be blank"))
                           .build();
        }
        if (request.deviceType == null || request.deviceType.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(new ErrorResponse("deviceType must not be blank"))
                           .build();
        }

        pushNotificationService.registerPushToken(
                request.clientId,
                request.pushToken,
                request.deviceType
        );

        return Response.ok(new RegisterPushTokenResponse(
                "Push token registered successfully",
                request.clientId
        )).build();
    }
}
