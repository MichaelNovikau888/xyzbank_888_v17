package com.bank.notification.service;

import com.bank.notification.client.ProfileServiceClient;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Сервис получения контактных данных клиента для уведомлений.
 */
 /**
 * Стратегия для email:
 *   1. Redis кэш «client:email:{clientId}» TTL=1ч
 *   2. REST-вызов к profile-service GET /api/profiles/{clientId}
 *   3. Fallback: null (caller должен пропустить email-уведомление)
 */
 /**
 * Push-токены управляются отдельно через PushNotificationService.registerPushToken().
 * ClientService про push-токены не знает — разделение ответственности.
 */
@ApplicationScoped
public class ClientService {

    private static final Logger   LOG         = Logger.getLogger(ClientService.class);
    private static final Duration EMAIL_TTL   = Duration.ofHours(1);
    private static final String   EMAIL_KEY   = "client:email:";

    @Inject
    @RestClient
    ProfileServiceClient profileClient;

    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<String, String> redis;

    @PostConstruct
    void init() {
        redis = redisDataSource.value(String.class);
    }

 /**
     * Package-private: только для тестов.
     * Позволяет подменить Redis-команды после @PostConstruct,
     * когда RedisDataSource замокирован через @InjectMock.
 */
    void initForTest(ValueCommands<String, String> mockRedis) {
        this.redis = mockRedis;
    }

 /**
     * Возвращает email клиента по clientId.
 */
 /**
     * clientId — строка. profile-service принимает Long id в пути, поэтому
     * пробуем парсить. Если clientId не числовой (legacy-формат) — возвращаем null.
 */
 /**
     * @return email или null если клиент не найден / profile-service недоступен
 */
    public String getClientEmail(String clientId) {
        if (clientId == null || clientId.isBlank()) return null;

        // 1. Redis cache
        try {
            String cached = redis.get(EMAIL_KEY + clientId);
            if (cached != null) {
                LOG.debugf("Email cache hit for client_id=%s", clientId);
                return cached;
            }
        } catch (Exception e) {
            LOG.warnf("Redis unavailable for email lookup client_id=%s, fetching from profile", clientId);
        }

        // 2. REST call to profile-service
        long profileId;
        try {
            profileId = Long.parseLong(clientId);
        } catch (NumberFormatException e) {
            LOG.warnf("clientId='%s' is not a numeric profile id, cannot fetch email", clientId);
            return null;
        }

        try {
            ProfileServiceClient.ProfileDto profile = profileClient.getById(profileId);
            String email = profile != null ? profile.email() : null;

            if (email != null && !email.isBlank()) {
                // Cache for 1 hour
                try {
                    redis.setex(EMAIL_KEY + clientId, EMAIL_TTL.getSeconds(), email);
                } catch (Exception e) {
                    LOG.warnf("Failed to cache email for client_id=%s", clientId);
                }
                LOG.debugf("Email fetched from profile-service: client_id=%s email=***@%s",
                           clientId, email.contains("@") ? email.split("@")[1] : "?");
            }

            return email;

        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch email from profile-service for client_id=%s", clientId);
            return null;
        }
    }
}
