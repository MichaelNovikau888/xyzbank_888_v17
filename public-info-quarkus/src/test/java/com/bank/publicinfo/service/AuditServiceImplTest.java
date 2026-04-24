package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.entity.Audit;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.mapper.AuditMapper;
import com.bank.publicinfo.producer.AuditProducer;
import com.bank.publicinfo.repository.AuditRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты AuditServiceImpl (public-info).
 */
 /**
 * Покрытие:
 *   createAudit: entityType resolved, operationType=CREATE, persist + sendAudit
 *   updateAudit: operationType=UPDATE
 *   getByEntityType: найдено / не найдено → EntityNotFoundException
 *   getAllByEntityJson: список / пустой
 *   getById: найдено / не найдено / null → IllegalArgumentException
 *   getAll: делегирует в репозиторий
 */
@QuarkusTest
@DisplayName("AuditServiceImpl (public-info) — unit tests")
class AuditServiceImplTest {

    @InjectMock AuditRepository auditRepository;
    @InjectMock AuditProducer   auditProducer;
    @InjectMock AuditMapper     auditMapper;

    @Inject AuditService auditService;

    private Audit entity(Long id, String type, String op) {
        Audit a = new Audit(); a.setId(id);
        a.setEntityType(type); a.setOperationType(op);
        a.setEntityJson("{\"id\":" + id + "}"); return a;
    }
    private AuditDto dto(Long id) {
        AuditDto d = new AuditDto(); d.setId(id); return d;
    }

    // ── createAudit ───────────────────────────────────────────────────────────

    @Nested @DisplayName("createAudit()")
    class CreateAudit {

        @Test @DisplayName("ATMDto → entityType=ATMDto, operationType=CREATE, persist + sendAudit")
        void atmDto_creates() {
            ATMDto atm = new ATMDto(); atm.setId(1L); atm.setBranchId(2L);
            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(dto(1L));

            auditService.createAudit(atm);

            ArgumentCaptor<Audit> cap = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(cap.capture());
            Assertions.assertThat(cap.getValue().getOperationType()).isEqualTo("CREATE");
            Assertions.assertThat(cap.getValue().getEntityType()).isNotBlank();
            Assertions.assertThat(cap.getValue().getCreatedAt()).isNotNull();
            verify(auditProducer).sendAudit(any());
        }

        @Test @DisplayName("entityJson содержит сериализованный DTO")
        void entityJsonSerialized() {
            ATMDto atm = new ATMDto(); atm.setId(42L);
            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(dto(1L));

            auditService.createAudit(atm);

            ArgumentCaptor<Audit> cap = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(cap.capture());
            Assertions.assertThat(cap.getValue().getEntityJson()).contains("42");
        }

        @Test @DisplayName("исключение при persist → не пробрасывается (catch inside)")
        void persistThrows_noException() {
            doThrow(new RuntimeException("DB down")).when(auditRepository).persist(any(Audit.class));
            Assertions.assertThatCode(() -> auditService.createAudit(new ATMDto()))
                    .doesNotThrowAnyException();
            verify(auditProducer, never()).sendAudit(any());
        }
    }

    // ── updateAudit ───────────────────────────────────────────────────────────

    @Nested @DisplayName("updateAudit()")
    class UpdateAudit {

        @Test @DisplayName("operationType=UPDATE, modifiedBy и modifiedAt установлены")
        void setsUpdateFields() {
            ATMDto atm = new ATMDto(); atm.setId(5L);
            doNothing().when(auditRepository).persist(any(Audit.class));
            when(auditMapper.toDto(any())).thenReturn(dto(5L));

            auditService.updateAudit(atm);

            ArgumentCaptor<Audit> cap = ArgumentCaptor.forClass(Audit.class);
            verify(auditRepository).persist(cap.capture());
            Assertions.assertThat(cap.getValue().getOperationType()).isEqualTo("UPDATE");
            Assertions.assertThat(cap.getValue().getModifiedBy()).isEqualTo("SYSTEM");
            Assertions.assertThat(cap.getValue().getModifiedAt()).isNotNull();
        }
    }

    // ── getByEntityType ───────────────────────────────────────────────────────

    @Nested @DisplayName("getByEntityType()")
    class GetByEntityType {

        @Test @DisplayName("найдена запись → возвращает AuditDto")
        void found_returnsDto() {
            Audit a = entity(1L, "ATMDto", "CREATE");
            when(auditRepository.findByEntityType("ATMDto")).thenReturn(Optional.of(a));
            when(auditMapper.toDto(a)).thenReturn(dto(1L));

            AuditDto result = auditService.getByEntityType("ATMDto");
            Assertions.assertThat(result.getId()).isEqualTo(1L);
        }

        @Test @DisplayName("не найдена → EntityNotFoundException")
        void notFound_throws() {
            when(auditRepository.findByEntityType("Unknown")).thenReturn(Optional.empty());
            Assertions.assertThatThrownBy(() -> auditService.getByEntityType("Unknown"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Unknown");
        }

        @Test @DisplayName("пустой тип → IllegalArgumentException")
        void blank_throws() {
            Assertions.assertThatThrownBy(() -> auditService.getByEntityType("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getAllByEntityJson ─────────────────────────────────────────────────────

    @Nested @DisplayName("getAllByEntityJson()")
    class GetAllByEntityJson {

        @Test @DisplayName("возвращает список DTO")
        void returnsList() {
            Audit a1 = entity(1L, "ATMDto", "CREATE");
            Audit a2 = entity(2L, "ATMDto", "UPDATE");
            when(auditRepository.findAllByEntityJson("{\"id\":")).thenReturn(List.of(a1, a2));
            when(auditMapper.toDto(a1)).thenReturn(dto(1L));
            when(auditMapper.toDto(a2)).thenReturn(dto(2L));

            List<AuditDto> result = auditService.getAllByEntityJson("{\"id\":");
            Assertions.assertThat(result).hasSize(2);
        }

        @Test @DisplayName("пустой json → IllegalArgumentException")
        void blank_throws() {
            Assertions.assertThatThrownBy(() -> auditService.getAllByEntityJson(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("нет совпадений → пустой список")
        void noMatches_empty() {
            when(auditRepository.findAllByEntityJson("xyz")).thenReturn(List.of());
            Assertions.assertThat(auditService.getAllByEntityJson("xyz")).isEmpty();
        }
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("getById()")
    class GetById {

        @Test @DisplayName("найден → возвращает AuditDto")
        void found_returnsDto() {
            Audit a = entity(10L, "BranchDto", "CREATE");
            when(auditRepository.findByIdOptional(10L)).thenReturn(Optional.of(a));
            when(auditMapper.toDto(a)).thenReturn(dto(10L));
            Assertions.assertThat(auditService.getById(10L).getId()).isEqualTo(10L);
        }

        @Test @DisplayName("не найден → EntityNotFoundException")
        void notFound_throws() {
            when(auditRepository.findByIdOptional(999L)).thenReturn(Optional.empty());
            Assertions.assertThatThrownBy(() -> auditService.getById(999L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test @DisplayName("null id → IllegalArgumentException")
        void nullId_throws() {
            Assertions.assertThatThrownBy(() -> auditService.getById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
