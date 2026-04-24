package com.bank.notification.service;

import com.bank.notification.client.ProfileServiceClient;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для ClientService (Quarkus).
 */
 /**
 * Покрываемые сценарии:
 */
 /**
 *   1. Email найден в Redis → REST к profile-service НЕ вызывается.
 *   2. Email не в Redis → REST-вызов → результат кэшируется в Redis.
 *   3. REST недоступен (исключение) → возвращается null, исключение НЕ пробрасывается.
 *   4. clientId не числовой → возвращается null без вызова REST.
 */
 /**
 * Дополнительно:
 *   5. clientId=null  → null, без Redis и REST.
 *   6. clientId=""    → null, без Redis и REST.
 *   7. Redis недоступен → fallback к REST (fail-open).
 */
@QuarkusTest
class ClientServiceTest {

    @Inject
    ClientService clientService;

    @InjectMock
    @RestClient
    ProfileServiceClient profileClient;

    @InjectMock
    RedisDataSource redisDataSource;

    // Используем raw mock для ValueCommands — тип параметризован, нужен unchecked cast
    @SuppressWarnings("unchecked")
    private ValueCommands<String, String> redis;

    @BeforeEach
    void setUp() {
        redis = Mockito.mock(ValueCommands.class);
        when(redisDataSource.value(String.class)).thenReturn(redis);
        // Пересоздаём Redis-команды через init() — имитируем @PostConstruct
        // Quarkus уже инициализировал бин, поэтому мокируем через возврат нашего mock
        // при следующем вызове. Поскольку init() вызван при старте,
        // нам нужно явно проставить поле через рефлексию или иначе.
        // Альтернатива: мокировать методы redis напрямую через шпиона на поле.
        // Поэтому используем подход с пересозданием через package-private метод.
        clientService.initForTest(redis);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 1: Email в Redis → REST не вызывается
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis cache hit → REST не вызывается, возвращается кэшированный email")
    void emailFoundInRedis_restNotCalled() {
        // Given
        String clientId = "42";
        String cachedEmail = "user@example.com";
        when(redis.get("client:email:" + clientId)).thenReturn(cachedEmail);

        // When
        String result = clientService.getClientEmail(clientId);

        // Then
        assertThat(result).isEqualTo(cachedEmail);
        verifyNoInteractions(profileClient);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 2: Email не в Redis → REST вызван → результат кэшируется
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis cache miss → REST вызван → email кэшируется в Redis")
    void emailNotInRedis_restCalledAndResultCached() {
        // Given
        String clientId = "99";
        String profileEmail = "john.doe@bank.ru";
        when(redis.get("client:email:" + clientId)).thenReturn(null); // cache miss
        when(profileClient.getById(99L))
                .thenReturn(new ProfileServiceClient.ProfileDto(99L, profileEmail, "+79001234567", "John Doe"));

        // When
        String result = clientService.getClientEmail(clientId);

        // Then
        assertThat(result).isEqualTo(profileEmail);
        // Проверяем, что email был закэширован с TTL=3600 (1 час)
        verify(redis).setex(eq("client:email:" + clientId), eq(3600L), eq(profileEmail));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 3: REST недоступен → null без исключения
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REST недоступен → возвращается null, исключение не пробрасывается")
    void restUnavailable_returnsNullWithoutException() {
        // Given
        String clientId = "7";
        when(redis.get(anyString())).thenReturn(null); // cache miss
        when(profileClient.getById(7L))
                .thenThrow(new RuntimeException("Connection refused: profile-service down"));

        // When / Then — исключение не должно выброситься наружу
        String result = clientService.getClientEmail(clientId);

        assertThat(result).isNull();
        // Redis setex не должен был вызваться (нечего кэшировать)
        verify(redis, never()).setex(anyString(), anyLong(), anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 4: clientId не числовой → null без вызова REST
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clientId не числовой → null, REST не вызывается")
    void nonNumericClientId_returnsNullWithoutRest() {
        // Given
        String clientId = "user-abc-123"; // не Long

        // When
        String result = clientService.getClientEmail(clientId);

        // Then
        assertThat(result).isNull();
        verifyNoInteractions(profileClient);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 5: clientId=null → null сразу
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clientId=null → null, Redis и REST не вызываются")
    void nullClientId_returnsNull() {
        String result = clientService.getClientEmail(null);

        assertThat(result).isNull();
        verifyNoInteractions(profileClient);
        verifyNoInteractions(redis);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 6: clientId="" → null сразу
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clientId пустой → null, Redis и REST не вызываются")
    void blankClientId_returnsNull() {
        String result = clientService.getClientEmail("   ");

        assertThat(result).isNull();
        verifyNoInteractions(profileClient);
        verifyNoInteractions(redis);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Сценарий 7: Redis недоступен → fail-open, REST вызывается
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis недоступен → fail-open, REST вызывается и email возвращается")
    void redisUnavailable_failOpenAndCallsRest() {
        // Given
        String clientId = "55";
        String profileEmail = "fallback@bank.ru";
        when(redis.get(anyString()))
                .thenThrow(new RuntimeException("Redis connection timeout"));
        when(profileClient.getById(55L))
                .thenReturn(new ProfileServiceClient.ProfileDto(55L, profileEmail, null, null));

        // When
        String result = clientService.getClientEmail(clientId);

        // Then — fail-open: даже без Redis возвращаем email из profile-service
        assertThat(result).isEqualTo(profileEmail);
        verify(profileClient).getById(55L);
    }
}
