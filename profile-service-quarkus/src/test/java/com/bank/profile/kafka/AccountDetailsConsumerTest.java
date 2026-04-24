package com.bank.profile.kafka;

import com.bank.profile.dto.AccountDetailsDto;
import com.bank.profile.entity.AccountDetails;
import com.bank.profile.kafka.consumer.AccountDetailsConsumer;
import com.bank.profile.kafka.producer.AccountDetailsProducer;
import com.bank.profile.metrics.ProfileMetrics;
import com.bank.profile.repository.AccountDetailsRepository;
import com.bank.profile.service.AccountDetailsService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты AccountDetailsConsumer (идемпотентность).
 */
@QuarkusTest
@DisplayName("AccountDetailsConsumer — unit tests (idempotency)")
class AccountDetailsConsumerTest {

    @InjectMock AccountDetailsService    accountDetailsService;
    @InjectMock AccountDetailsProducer   accountDetailsProducer;
    @InjectMock AccountDetailsRepository accountDetailsRepository;
    @InjectMock ProfileMetrics           metrics;

    @Inject AccountDetailsConsumer consumer;

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = mock(Counter.class);
        when(metrics.getAccountDetailsCreated()).thenReturn(mockCounter);
        when(metrics.getAccountDetailsUpdated()).thenReturn(mockCounter);
        when(metrics.getAccountDetailsDeleted()).thenReturn(mockCounter);
        when(metrics.getAccountDetailsIdempotentSkipped()).thenReturn(mockCounter);
    }

    private AccountDetailsDto dto(Long id) {
        AccountDetailsDto d = new AccountDetailsDto();
        d.setId(id);
        return d;
    }

    private AccountDetails entity(Long id) {
        AccountDetails e = new AccountDetails();
        // just a non-null entity to satisfy isPresent()
        return e;
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("новый id → сервис вызывается, created++")
        void newId_callsService() {
            when(accountDetailsRepository.findByIdOptional(1L)).thenReturn(Optional.empty());

            consumer.create(dto(1L));

            verify(accountDetailsService).create(any());
            verify(mockCounter).increment();
        }

        @Test
        @DisplayName("дубль id → сервис НЕ вызывается, skipped++")
        void duplicateId_skips() {
            when(accountDetailsRepository.findByIdOptional(2L))
                    .thenReturn(Optional.of(entity(2L)));

            consumer.create(dto(2L));

            verify(accountDetailsService, never()).create(any());
            verify(metrics.getAccountDetailsIdempotentSkipped()).increment();
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("запись существует → сервис вызывается, updated++")
        void exists_callsService() {
            when(accountDetailsRepository.findByIdOptional(3L))
                    .thenReturn(Optional.of(entity(3L)));

            consumer.update(dto(3L));

            verify(accountDetailsService).update(anyLong(), any());
            verify(metrics.getAccountDetailsUpdated()).increment();
        }

        @Test
        @DisplayName("запись не найдена → сервис НЕ вызывается, skipped++")
        void notFound_skips() {
            when(accountDetailsRepository.findByIdOptional(4L)).thenReturn(Optional.empty());

            consumer.update(dto(4L));

            verify(accountDetailsService, never()).update(anyLong(), any());
            verify(metrics.getAccountDetailsIdempotentSkipped()).increment();
        }

        @Test
        @DisplayName("null id → сервис НЕ вызывается")
        void nullId_skips() {
            consumer.update(dto(null));

            verify(accountDetailsService, never()).update(anyLong(), any());
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("запись существует → сервис вызывается, deleted++")
        void exists_callsService() {
            when(accountDetailsRepository.findByIdOptional(5L))
                    .thenReturn(Optional.of(entity(5L)));

            consumer.delete(dto(5L));

            verify(accountDetailsService).delete(5L);
            verify(metrics.getAccountDetailsDeleted()).increment();
        }

        @Test
        @DisplayName("уже удалена → сервис НЕ вызывается, skipped++")
        void alreadyDeleted_skips() {
            when(accountDetailsRepository.findByIdOptional(6L)).thenReturn(Optional.empty());

            consumer.delete(dto(6L));

            verify(accountDetailsService, never()).delete(anyLong());
            verify(metrics.getAccountDetailsIdempotentSkipped()).increment();
        }
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("get → всегда вызывает сервис и отправляет ответ")
        void alwaysCallsServiceAndProducer() {
            AccountDetailsDto response = dto(7L);
            when(accountDetailsService.get(7L)).thenReturn(response);

            consumer.get(dto(7L));

            verify(accountDetailsService).get(7L);
            verify(accountDetailsProducer).sendGetResponse(response);
        }
    }
}
