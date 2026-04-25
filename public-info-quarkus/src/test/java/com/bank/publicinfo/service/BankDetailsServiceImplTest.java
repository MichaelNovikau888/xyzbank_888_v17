package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.exception.ValidationException;
import com.bank.publicinfo.mapper.BankDetailsMapper;
import com.bank.publicinfo.producer.BankDetailsProducer;
import com.bank.publicinfo.repository.BankDetailsRepository;
import com.bank.publicinfo.repository.CertificateRepository;
import com.bank.publicinfo.repository.LicenseRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты BankDetailsServiceImpl.
 */
 /**
 * Покрытие:
 *   create: успех; null dto → IAE; дублирующий BIK → ValidationException
 *   update: успех; null dto → ValidationException; не найден → EntityNotFoundException
 *   deleteById: успех (каскад лицензии/сертификаты); null id → IAE; не найден → ENFE
 *   getById: найден; null → IAE; не найден → ENFE
 */
@QuarkusTest
@DisplayName("BankDetailsServiceImpl — unit tests")
class BankDetailsServiceImplTest {

    @InjectMock BankDetailsRepository repository;
    @InjectMock LicenseRepository     licenseRepo;
    @InjectMock CertificateRepository certRepo;
    @InjectMock BankDetailsMapper     mapper;
    @InjectMock BankDetailsProducer   producer;

    @Inject BankDetailsService service;

    private BankDetails entity(Long id, Long bik) {
        BankDetails e = new BankDetails(); e.setId(id); e.setBik(bik); return e;
    }
    private BankDetailsDto dto(Long id, Long bik) {
        BankDetailsDto d = new BankDetailsDto(); d.setId(id); d.setBik(bik); return d;
    }

    @BeforeEach void resetProducer() { doNothing().when(producer).sendCreated(any()); }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create()")
    class Create {

        @Test @DisplayName("успешное создание: persist + sendCreated вызваны")
        void success_persistsAndSends() {
            BankDetailsDto d = dto(null, 123456789L);
            BankDetails e   = entity(1L, 123456789L);
            when(repository.findByBik(123456789L)).thenReturn(Optional.empty());
            when(mapper.toEntity(d)).thenReturn(e);
            when(mapper.toDto(e)).thenReturn(dto(1L, 123456789L));
            doNothing().when(repository).persist(e);

            BankDetailsDto result = service.create(d);

            verify(repository).persist(e);
            verify(producer).sendCreated(any());
            Assertions.assertThat(result.getId()).isEqualTo(1L);
        }

        @Test @DisplayName("null dto → IllegalArgumentException")
        void nullDto_throws() {
            Assertions.assertThatThrownBy(() -> service.create(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("дублирующий BIK → ValidationException")
        void duplicateBik_throwsValidationException() {
            BankDetailsDto d = dto(null, 999999999L);
            when(repository.findByBik(999999999L)).thenReturn(Optional.of(entity(1L, 999999999L)));

            Assertions.assertThatThrownBy(() -> service.create(d))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("999999999");
            verify(repository, never()).persist(any(BankDetails.class));
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update()")
    class Update {

        @Test @DisplayName("успешное обновление: persist + sendUpdated")
        void success() {
            BankDetailsDto d = dto(1L, 111111111L);
            BankDetails e    = entity(1L, 111111111L);
            when(repository.findByIdOptional(1L)).thenReturn(Optional.of(e));
            doNothing().when(repository).persist(e);
            when(mapper.toDto(e)).thenReturn(d);
            doNothing().when(mapper).updateFromDto(d, e);
            doNothing().when(producer).sendUpdated(any());

            service.update(d);

            verify(repository).persist(e);
            verify(producer).sendUpdated(any());
        }

        @Test @DisplayName("null dto → ValidationException")
        void nullDto_throws() {
            Assertions.assertThatThrownBy(() -> service.update(null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test @DisplayName("dto с null id → ValidationException")
        void nullId_throws() {
            Assertions.assertThatThrownBy(() -> service.update(dto(null, 123L)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test @DisplayName("не найден → EntityNotFoundException")
        void notFound_throws() {
            when(repository.findByIdOptional(999L)).thenReturn(Optional.empty());
            Assertions.assertThatThrownBy(() -> service.update(dto(999L, 123L)))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Nested @DisplayName("deleteById()")
    class DeleteById {

        @Test @DisplayName("успешное удаление: каскад licenseRepo + certRepo + delete + sendDeleted")
        void success_cascadeDelete() {
            BankDetails e = entity(1L, 123L);
            when(repository.findByIdOptional(1L)).thenReturn(Optional.of(e));
            doNothing().when(licenseRepo).deleteByBankDetailsId(1L);
            doNothing().when(certRepo).deleteByBankDetailsId(1L);
            doNothing().when(repository).delete(e);
            doNothing().when(producer).sendDeleted(1L);

            service.deleteById(1L);

            verify(licenseRepo).deleteByBankDetailsId(1L);
            verify(certRepo).deleteByBankDetailsId(1L);
            verify(repository).delete(e);
            verify(producer).sendDeleted(1L);
        }

        @Test @DisplayName("null id → IllegalArgumentException")
        void nullId_throws() {
            Assertions.assertThatThrownBy(() -> service.deleteById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("не найден → EntityNotFoundException")
        void notFound_throws() {
            when(repository.findByIdOptional(88L)).thenReturn(Optional.empty());
            Assertions.assertThatThrownBy(() -> service.deleteById(88L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("getById()")
    class GetById {

        @Test @DisplayName("найден → возвращает DTO")
        void found_returnsDto() {
            BankDetails e = entity(2L, 222L);
            when(repository.findByIdOptional(2L)).thenReturn(Optional.of(e));
            when(mapper.toDto(e)).thenReturn(dto(2L, 222L));
            Assertions.assertThat(service.getById(2L).getId()).isEqualTo(2L);
        }

        @Test @DisplayName("null id → IllegalArgumentException")
        void nullId_throws() {
            Assertions.assertThatThrownBy(() -> service.getById(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("не найден → EntityNotFoundException")
        void notFound_throws() {
            when(repository.findByIdOptional(777L)).thenReturn(Optional.empty());
            Assertions.assertThatThrownBy(() -> service.getById(777L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
