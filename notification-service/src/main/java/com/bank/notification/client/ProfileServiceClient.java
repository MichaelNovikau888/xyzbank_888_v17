package com.bank.notification.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST-клиент к profile-service.
 */
 /**
 * Базовый URL задаётся в application.properties:
 *   quarkus.rest-client.profile-api.url=http://localhost:8089
 */
 /**
 * Fault tolerance:
 *   @Timeout(2000) — не блокируем notification-service если profile упал.
 *   @Fallback      — при таймауте или ошибке возвращаем null-ProfileDto.
 *                    ClientService обработает null: вернёт null → email пропускается.
 */
@Path("/api/profiles")
@RegisterRestClient(configKey = "profile-api")
@Produces(MediaType.APPLICATION_JSON)
public interface ProfileServiceClient {

 /**
     * Возвращает профиль клиента по id.
 */
    @GET
    @Path("/{id}")
    @Timeout(2000)
    @Fallback(fallbackMethod = "getByIdFallback")
    ProfileDto getById(@PathParam("id") long id);

 /**
     * Fallback: вызывается при таймауте или любой ошибке getById().
     * Возвращает null — ClientService вернёт null, email не отправится.
     * Предпочтительнее, чем бросать исключение и ломать уведомление.
 */
    default ProfileDto getByIdFallback(long id) {
        return null;
    }

 /**
     * DTO для получения email из profile-service.
     * Содержит только поля, нужные notification-service — не тянем весь ProfileDto.
 */
    record ProfileDto(Long id, String email, String phoneNumber, String nameOnCard) {}
}
