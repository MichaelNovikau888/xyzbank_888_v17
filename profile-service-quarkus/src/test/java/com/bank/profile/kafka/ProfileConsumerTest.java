package com.bank.profile.kafka;

import com.bank.profile.dto.ProfileDto;
import com.bank.profile.entity.Profile;
import com.bank.profile.kafka.consumer.ProfileConsumer;
import com.bank.profile.kafka.producer.ProfileProducer;
import com.bank.profile.metrics.ProfileMetrics;
import com.bank.profile.repository.ProfileRepository;
import com.bank.profile.service.ProfileService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты ProfileConsumer.
 * Проверяем логику идемпотентности для операций create/update/delete.
 * ProfileService, ProfileRepository и ProfileMetrics — моки, Kafka не нужна.
 */
@QuarkusTest
class ProfileConsumerTest {

    @InjectMock
    ProfileService profileService;

    @InjectMock
    ProfileRepository profileRepository;

    @InjectMock
    ProfileProducer profileProducer;

    @InjectMock
    ProfileMetrics metrics;

    @Inject
    ProfileConsumer consumer;

    @BeforeEach
    void setUp() {
        Counter mockCounter = mock(Counter.class);
        when(metrics.getProfilesCreated()).thenReturn(mockCounter);
        when(metrics.getProfilesUpdated()).thenReturn(mockCounter);
        when(metrics.getProfilesDeleted()).thenReturn(mockCounter);
        when(metrics.getProfileIdempotentSkipped()).thenReturn(mockCounter);
    }

    private ProfileDto buildDto(Long id, Long snils) {
        ProfileDto dto = new ProfileDto();
        dto.setId(id);
        dto.setSnils(snils);
        dto.setEmail("test" + id + "@example.com");
        return dto;
    }

    private Profile buildProfile(Long id) {
        Profile p = new Profile();
        p.setId(id);
        return p;
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: новый профиль (snils и id отсутствуют в БД) — profileService.create вызывается")
    void create_newProfile_callsCreate() {
        ProfileDto dto = buildDto(1L, 11100000001L);
        when(profileRepository.findBySnils(dto.getSnils())).thenReturn(Optional.empty());
        when(profileRepository.findByIdOptional(dto.getId())).thenReturn(Optional.empty());

        consumer.create(dto);

        verify(profileService).create(dto);
        verify(metrics.getProfilesCreated()).increment();
    }

    @Test
    @DisplayName("create: дубль по snils — profileService.create НЕ вызывается, skipped ++")
    void create_duplicateSnils_skips() {
        ProfileDto dto = buildDto(2L, 22200000002L);
        when(profileRepository.findBySnils(dto.getSnils()))
                .thenReturn(Optional.of(buildProfile(2L)));

        consumer.create(dto);

        verify(profileService, never()).create(any());
        verify(metrics.getProfileIdempotentSkipped()).increment();
    }

    @Test
    @DisplayName("create: дубль по id — profileService.create НЕ вызывается, skipped ++")
    void create_duplicateId_skips() {
        ProfileDto dto = buildDto(3L, 33300000003L);
        when(profileRepository.findBySnils(dto.getSnils())).thenReturn(Optional.empty());
        when(profileRepository.findByIdOptional(dto.getId())).thenReturn(Optional.of(buildProfile(3L)));

        consumer.create(dto);

        verify(profileService, never()).create(any());
        verify(metrics.getProfileIdempotentSkipped()).increment();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: профиль существует — profileService.update вызывается")
    void update_exists_callsUpdate() {
        ProfileDto dto = buildDto(10L, 10100000010L);
        when(profileRepository.findByIdOptional(10L)).thenReturn(Optional.of(buildProfile(10L)));

        consumer.update(dto);

        verify(profileService).update(10L, dto);
        verify(metrics.getProfilesUpdated()).increment();
    }

    @Test
    @DisplayName("update: профиль не найден — profileService.update НЕ вызывается, skipped ++")
    void update_notFound_skips() {
        ProfileDto dto = buildDto(11L, 11100000011L);
        when(profileRepository.findByIdOptional(11L)).thenReturn(Optional.empty());

        consumer.update(dto);

        verify(profileService, never()).update(any(), any());
        verify(metrics.getProfileIdempotentSkipped()).increment();
    }

    @Test
    @DisplayName("update: id == null — profileService.update НЕ вызывается, skipped ++")
    void update_nullId_skips() {
        ProfileDto dto = buildDto(null, 12100000012L);

        consumer.update(dto);

        verify(profileService, never()).update(any(), any());
        verify(metrics.getProfileIdempotentSkipped()).increment();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: профиль существует — profileService.delete вызывается")
    void delete_exists_callsDelete() {
        ProfileDto dto = buildDto(20L, 20200000020L);
        when(profileRepository.findByIdOptional(20L)).thenReturn(Optional.of(buildProfile(20L)));

        consumer.delete(dto);

        verify(profileService).delete(20L);
        verify(metrics.getProfilesDeleted()).increment();
    }

    @Test
    @DisplayName("delete: профиль уже удалён — profileService.delete НЕ вызывается, skipped ++")
    void delete_alreadyDeleted_skips() {
        ProfileDto dto = buildDto(21L, 21200000021L);
        when(profileRepository.findByIdOptional(21L)).thenReturn(Optional.empty());

        consumer.delete(dto);

        verify(profileService, never()).delete(any());
        verify(metrics.getProfileIdempotentSkipped()).increment();
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: вызывает profileService.get и отправляет ответ через producer")
    void get_callsServiceAndProducer() {
        ProfileDto dto = buildDto(30L, 30300000030L);
        ProfileDto response = buildDto(30L, 30300000030L);
        when(profileService.get(30L)).thenReturn(response);

        consumer.get(dto);

        verify(profileService).get(30L);
        verify(profileProducer).sendGetResponse(response);
    }
}
