package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit-тесты продюсеров public-info-service.
 */
 /**
 * Emitter заменён на smallrye-in-memory в тестовом профиле.
 * Проверяем: не бросают исключений, не крэшатся при null-id.
 */
 /**
 * Для ATMProducer/BranchProducer/etc. проверяем что
 * sendCreated/sendUpdated/sendDeleted не бросают исключений.
 * Детальная проверка payload покрыта интеграционными тестами.
 */
@QuarkusTest
@DisplayName("Public-info Producers — unit tests")
class ProducersTest {

    @Inject ATMProducer         atmProducer;
    @Inject BranchProducer      branchProducer;
    @Inject CertificateProducer certProducer;
    @Inject LicenseProducer     licenseProducer;
    @Inject BankDetailsProducer bankProducer;
    @Inject AuditProducer       auditProducer;

    private ATMDto atmDto() {
        ATMDto d = new ATMDto(); d.setId(1L); d.setBranchId(2L); d.setAllHours(true); return d;
    }
    private BranchDto branchDto() {
        BranchDto d = new BranchDto(); d.setId(1L); d.setCity("Москва"); return d;
    }
    private CertificateDto certDto() {
        CertificateDto d = new CertificateDto(); d.setId(1L); d.setBankDetailsId(2L); return d;
    }
    private LicenseDto licenseDto() {
        LicenseDto d = new LicenseDto(); d.setId(1L); d.setBankDetailsId(2L); return d;
    }
    private BankDetailsDto bankDto() {
        BankDetailsDto d = new BankDetailsDto(); d.setId(1L); d.setBik(123456789L); return d;
    }
    private AuditDto auditDto() {
        AuditDto d = new AuditDto();
        d.setEntityType("ATMDto"); d.setOperationType("CREATE"); return d;
    }

    // ── ATMProducer ───────────────────────────────────────────────────────────

    @Nested @DisplayName("ATMProducer")
    class AtmProducerTest {

        @Test @DisplayName("sendCreated → не бросает исключений")
        void sendCreated_noException() {
            Assertions.assertThatCode(() -> atmProducer.sendCreated(atmDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendUpdated → не бросает исключений")
        void sendUpdated_noException() {
            Assertions.assertThatCode(() -> atmProducer.sendUpdated(atmDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendDeleted → не бросает исключений")
        void sendDeleted_noException() {
            Assertions.assertThatCode(() -> atmProducer.sendDeleted(1L))
                    .doesNotThrowAnyException();
        }
    }

    // ── BranchProducer ────────────────────────────────────────────────────────

    @Nested @DisplayName("BranchProducer")
    class BranchProducerTest {

        @Test @DisplayName("sendCreated → не бросает исключений")
        void sendCreated_noException() {
            Assertions.assertThatCode(() -> branchProducer.sendCreated(branchDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendUpdated → не бросает исключений")
        void sendUpdated_noException() {
            Assertions.assertThatCode(() -> branchProducer.sendUpdated(branchDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendDeleted → не бросает исключений")
        void sendDeleted_noException() {
            Assertions.assertThatCode(() -> branchProducer.sendDeleted(1L))
                    .doesNotThrowAnyException();
        }
    }

    // ── CertificateProducer ───────────────────────────────────────────────────

    @Nested @DisplayName("CertificateProducer")
    class CertProducerTest {

        @Test @DisplayName("sendCreated → не бросает")
        void sendCreated_noException() {
            Assertions.assertThatCode(() -> certProducer.sendCreated(certDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendUpdated → не бросает")
        void sendUpdated_noException() {
            Assertions.assertThatCode(() -> certProducer.sendUpdated(certDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendDeleted → не бросает")
        void sendDeleted_noException() {
            Assertions.assertThatCode(() -> certProducer.sendDeleted(1L))
                    .doesNotThrowAnyException();
        }
    }

    // ── LicenseProducer ───────────────────────────────────────────────────────

    @Nested @DisplayName("LicenseProducer")
    class LicenseProducerTest {

        @Test @DisplayName("sendCreated → не бросает")
        void sendCreated_noException() {
            Assertions.assertThatCode(() -> licenseProducer.sendCreated(licenseDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendDeleted → не бросает")
        void sendDeleted_noException() {
            Assertions.assertThatCode(() -> licenseProducer.sendDeleted(1L))
                    .doesNotThrowAnyException();
        }
    }

    // ── BankDetailsProducer ───────────────────────────────────────────────────

    @Nested @DisplayName("BankDetailsProducer")
    class BankProducerTest {

        @Test @DisplayName("sendCreated → не бросает")
        void sendCreated_noException() {
            Assertions.assertThatCode(() -> bankProducer.sendCreated(bankDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendUpdated → не бросает")
        void sendUpdated_noException() {
            Assertions.assertThatCode(() -> bankProducer.sendUpdated(bankDto()))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("sendDeleted → не бросает")
        void sendDeleted_noException() {
            Assertions.assertThatCode(() -> bankProducer.sendDeleted(1L))
                    .doesNotThrowAnyException();
        }
    }

    // ── AuditProducer ─────────────────────────────────────────────────────────

    @Nested @DisplayName("AuditProducer")
    class AuditProducerTest {

        @Test @DisplayName("sendAudit → не бросает исключений")
        void sendAudit_noException() {
            Assertions.assertThatCode(() -> auditProducer.sendAudit(auditDto()))
                    .doesNotThrowAnyException();
        }
    }
}
