package com.bank.profile.service;

import com.bank.profile.dto.ProfileDto;
import com.bank.profile.entity.Audit;
import com.bank.profile.kafka.producer.AuditProducer;
import com.bank.profile.mapper.AuditMapper;
import com.bank.profile.repository.AuditRepository;
import com.bank.profile.service.impl.AuditServiceImpl;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты AuditServiceImpl (profile-service).
 */
 /**
 * Покрываемые сценарии:
 *   create(): известный тип → entityType + OperationType.Create + persist + sendAudit
 *   create(): неизвестный тип → entityType=None, не бросает исключение
 *   update(): находит существующую запись → обновляет modifiedBy/At/newEntityJson
 *   update(): не находит запись → ничего не делает (idempotent)
 */
@QuarkusTest
@DisplayName("AuditServiceImpl — unit tests")
class AuditServiceImplTest {

    @InjectMock AuditRepository auditRepository;
    @InjectMock AuditProducer   auditProducer;
    @InjectMock AuditMapper     auditMapper;

    @Inject AuditService auditService;

    // ── create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("ProfileDto → entityType=Profile, operationType=Create, persist вызывается")
        void knownType_persistsAndSendsAudit() {
            ProfileDto dto = new ProfileDto();
            dto.setId(1L);
            dto.setEmail("test@example.com");

            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(new com.bank.profile.dto.AuditDto());

            auditService.create(dto);

            ArgumentCaptor<Audit> captor = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(captor.capture());
            Assertions.assertThat(captor.getValue().getEntityType()).isEqualTo("Profile");
            Assertions.assertThat(captor.getValue().getOperationType()).isEqualTo("Create");
            Assertions.assertThat(captor.getValue().getCreatedBy()).isEqualTo("user");
            Assertions.assertThat(captor.getValue().getCreatedAt()).isNotNull();
            verify(auditProducer).sendAudit(any());
        }

        @Test
        @DisplayName("неизвестный тип (String) → entityType=None, исключение не бросается")
        void unknownType_usesNoneFallback() {
            // String не в EntityType enum → IllegalArgumentException внутри → fallback None
            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(new com.bank.profile.dto.AuditDto());

            Assertions.assertThatCode(() -> auditService.create("plain string"))
                    .doesNotThrowAnyException();

            ArgumentCaptor<Audit> captor = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(captor.capture());
            Assertions.assertThat(captor.getValue().getEntityType()).isEqualTo("woops"); // EntityType.None
        }

        @Test
        @DisplayName("entityJson содержит сериализованный DTO")
        void entityJson_containsSerializedDto() {
            ProfileDto dto = new ProfileDto();
            dto.setId(42L);
            dto.setEmail("audit@test.com");

            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(new com.bank.profile.dto.AuditDto());

            auditService.create(dto);

            ArgumentCaptor<Audit> captor = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(captor.capture());
            Assertions.assertThat(captor.getValue().getEntityJson())
                    .contains("42")
                    .contains("audit@test.com");
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("запись найдена → modifiedBy, modifiedAt, newEntityJson обновлены")
        void found_updatesAuditRecord() {
            ProfileDto dto = new ProfileDto();
            dto.setId(5L);
            dto.setEmail("updated@test.com");

            Audit existing = new Audit();
            existing.setEntityType("Profile");
            existing.setEntityJson("{\"id\":5}");

            when(auditRepository.findByEntityTypeAndEntityJsonContains(anyString(), anyString()))
                    .thenReturn(Optional.of(existing));
            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(new com.bank.profile.dto.AuditDto());

            auditService.update(dto);

            Assertions.assertThat(existing.getModifiedBy()).isEqualTo("user");
            Assertions.assertThat(existing.getModifiedAt()).isNotNull();
            Assertions.assertThat(existing.getNewEntityJson()).contains("updated@test.com");
            verify(auditRepository).persist(existing);
            verify(auditProducer).sendAudit(any());
        }

        @Test
        @DisplayName("запись не найдена → ничего не делается (идемпотентно)")
        void notFound_noop() {
            ProfileDto dto = new ProfileDto();
            dto.setId(999L);

            when(auditRepository.findByEntityTypeAndEntityJsonContains(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            auditService.update(dto);

            verify(auditRepository, never()).persist(any());
            verify(auditProducer, never()).sendAudit(any());
        }

        @Test
        @DisplayName("dto без id → ничего не делается (нет idFragment)")
        void dtoWithoutId_noop() {
            ProfileDto dto = new ProfileDto();
            dto.setEmail("no-id@test.com");
            // id = null → json не содержит "id": → matcher не найдёт → noop

            auditService.update(dto);

            verify(auditRepository, never()).persist(any());
        }
    }
}
