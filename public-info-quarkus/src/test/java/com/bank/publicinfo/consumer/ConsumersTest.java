package com.bank.publicinfo.consumer;

import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.ATMService;
import com.bank.publicinfo.service.BankDetailsService;
import com.bank.publicinfo.service.BranchService;
import com.bank.publicinfo.service.CertificateService;
import com.bank.publicinfo.service.LicenseService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты Kafka-консьюмеров public-info-service.
 * Сервисы и метрики — моки. Kafka не нужна.
 */

/**
 * Покрытие:
 * BankDetailsConsumer: create/update/delete → сервис + метрика; invalud JSON → ошибка не пробрасывается
 * BranchConsumer:      create/update/delete → сервис + метрика; null id в update → skip
 * ATMConsumer:         create/update/delete → сервис + метрика; null id в delete → skip
 * CertificateConsumer: create/update/delete → базовые сценарии
 * LicenseConsumer:     create/update/delete → базовые сценарии
 * ErrorLogsConsumer:   handleErrorLog → не бросает исключений
 */
@QuarkusTest
@DisplayName("Public-info Kafka Consumers — unit tests")
class ConsumersTest {

    @InjectMock
    BankDetailsService bankService;
    @InjectMock
    BranchService branchService;
    @InjectMock
    ATMService atmService;
    @InjectMock
    CertificateService certService;
    @InjectMock
    LicenseService licenseService;
    @InjectMock
    PublicInfoMetrics metrics;

    @Inject
    BankDetailsConsumer bankConsumer;
    @Inject
    BranchConsumer branchConsumer;
    @Inject
    ATMConsumer atmConsumer;
    @Inject
    CertificateConsumer certConsumer;
    @Inject
    LicenseConsumer licenseConsumer;
    @Inject
    ErrorLogsConsumer errorConsumer;

    private Counter ok;
    private Counter err;

    @BeforeEach
    void setUp() {
        ok = mock(Counter.class);
        err = mock(Counter.class);
        when(metrics.getBankDetailsCreated()).thenReturn(ok);
        when(metrics.getBankDetailsUpdated()).thenReturn(ok);
        when(metrics.getBankDetailsDeleted()).thenReturn(ok);
        when(metrics.getBranchCreated()).thenReturn(ok);
        when(metrics.getBranchUpdated()).thenReturn(ok);
        when(metrics.getBranchDeleted()).thenReturn(ok);
        when(metrics.getAtmCreated()).thenReturn(ok);
        when(metrics.getAtmUpdated()).thenReturn(ok);
        when(metrics.getAtmDeleted()).thenReturn(ok);
        when(metrics.getLicenseCreated()).thenReturn(ok);
        when(metrics.getCertificateCreated()).thenReturn(ok);
        when(metrics.getKafkaErrors()).thenReturn(err);
    }

    // ── BankDetailsConsumer ───────────────────────────────────────────────────

    @Nested
    @DisplayName("BankDetailsConsumer")
    class BankDetails {

        @Test
        @DisplayName("onCreate: валидный JSON → create() + метрика++")
        void onCreate_valid() {
            bankConsumer.onCreate("{\"id\":1,\"bik\":123456789}");
            verify(bankService).create(any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onUpdate: валидный JSON → update() + метрика++")
        void onUpdate_valid() {
            bankConsumer.onUpdate("{\"id\":1,\"bik\":123456789}");
            verify(bankService).update(any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onDelete: валидный id → deleteById() + метрика++")
        void onDelete_valid() {
            bankConsumer.onDelete("{\"id\":5}");
            verify(bankService).deleteById(5L);
            verify(ok).increment();
        }

        @Test
        @DisplayName("onDelete: null id → сервис НЕ вызывается")
        void onDelete_nullId_skips() {
            bankConsumer.onDelete("{\"bik\":999}");
            verify(bankService, never()).deleteById(any());
            verify(err, never()).increment();
        }

        @Test
        @DisplayName("onCreate: невалидный JSON → kafkaErrors++, без exception")
        void onCreate_invalidJson_noException() {
            Assertions.assertThatCode(() -> bankConsumer.onCreate("NOT_JSON"))
                    .doesNotThrowAnyException();
            verify(err).increment();
            verify(bankService, never()).create(any());
        }

        @Test
        @DisplayName("onUpdate: невалидный JSON → kafkaErrors++")
        void onUpdate_invalidJson() {
            bankConsumer.onUpdate("{invalid}");
            verify(err).increment();
        }
    }

    // ── BranchConsumer ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BranchConsumer")
    class Branch {

        @Test
        @DisplayName("onCreate: валидный JSON → create() + метрика++")
        void onCreate_valid() {
            branchConsumer.onCreate("{\"id\":1,\"address\":\"ул. Ленина\",\"city\":\"Москва\"}");
            verify(branchService).create(any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onUpdate: null id → сервис НЕ вызывается")
        void onUpdate_nullId_skips() {
            branchConsumer.onUpdate("{\"address\":\"ул. Ленина\"}");
            verify(branchService, never()).update(any(), any());
        }

        @Test
        @DisplayName("onUpdate: с id → update() + метрика++")
        void onUpdate_withId() {
            branchConsumer.onUpdate("{\"id\":3,\"address\":\"ул. Мира\",\"city\":\"СПб\"}");
            verify(branchService).update(eq(3L), any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onDelete: с id → deleteById() + метрика++")
        void onDelete_withId() {
            branchConsumer.onDelete("{\"id\":7}");
            verify(branchService).deleteById(7L);
            verify(ok).increment();
        }

        @Test
        @DisplayName("onCreate: невалидный JSON → kafkaErrors++")
        void onCreate_invalidJson() {
            branchConsumer.onCreate("BAD_JSON");
            verify(err).increment();
            verify(branchService, never()).create(any());
        }
    }

    // ── ATMConsumer ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ATMConsumer")
    class ATM {

        @Test
        @DisplayName("onCreate: валидный JSON → create() + метрика++")
        void onCreate_valid() {
            atmConsumer.onCreate("{\"id\":1,\"branchId\":2,\"allHours\":true}");
            verify(atmService).create(any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onUpdate: с id → update() + метрика++")
        void onUpdate_withId() {
            atmConsumer.onUpdate("{\"id\":4,\"branchId\":1,\"allHours\":false}");
            verify(atmService).update(any());
            verify(ok).increment();
        }

        @Test
        @DisplayName("onUpdate: null id → сервис НЕ вызывается")
        void onUpdate_nullId_skips() {
            atmConsumer.onUpdate("{\"branchId\":1}");
            verify(atmService, never()).update(any());
        }

        @Test
        @DisplayName("onDelete: с id → deleteById() + метрика++")
        void onDelete_withId() {
            atmConsumer.onDelete("{\"id\":9}");
            verify(atmService).deleteById(9L);
            verify(ok).increment();
        }

        @Test
        @DisplayName("onDelete: null id → сервис НЕ вызывается")
        void onDelete_nullId_skips() {
            atmConsumer.onDelete("{\"allHours\":true}");
            verify(atmService, never()).deleteById(any());
        }

        @Test
        @DisplayName("onCreate: невалидный JSON → kafkaErrors++")
        void onCreate_invalidJson() {
            atmConsumer.onCreate("{{invalid");
            verify(err).increment();
        }
    }

    // ── CertificateConsumer ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CertificateConsumer")
    class Certificate {

        @Test
        @DisplayName("onCreate → create() вызывается")
        void onCreate_callsService() {
            certConsumer.onCreate("{\"id\":1,\"bankDetailsId\":2}");
            verify(certService).create(any());
        }

        @Test
        @DisplayName("onUpdate → update() вызывается")
        void onUpdate_callsService() {
            certConsumer.onUpdate("{\"id\":1,\"bankDetailsId\":2}");
            verify(certService).update(any());
        }

        @Test
        @DisplayName("onDelete: с id → deleteById()")
        void onDelete_withId() {
            certConsumer.onDelete("{\"id\":5}");
            verify(certService).deleteById(5L);
        }

        @Test
        @DisplayName("onDelete: null id → сервис НЕ вызывается")
        void onDelete_nullId_skips() {
            certConsumer.onDelete("{\"bankDetailsId\":2}");
            verify(certService, never()).deleteById(any());
        }

        @Test
        @DisplayName("onCreate: невалидный JSON → kafkaErrors++")
        void onCreate_invalidJson() {
            certConsumer.onCreate("NOT_JSON");
            verify(err).increment();
        }
    }

    // ── LicenseConsumer ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("LicenseConsumer")
    class License {

        @Test
        @DisplayName("onCreate → create() вызывается")
        void onCreate_callsService() {
            licenseConsumer.onCreate("{\"id\":1,\"bankDetailsId\":2}");
            verify(licenseService).create(any());
        }

        @Test
        @DisplayName("onUpdate → update() вызывается")
        void onUpdate_callsService() {
            licenseConsumer.onUpdate("{\"id\":1,\"bankDetailsId\":2}");
            verify(licenseService).update(any());
        }

        @Test
        @DisplayName("onDelete: с id → deleteById()")
        void onDelete_withId() {
            licenseConsumer.onDelete("{\"id\":3}");
            verify(licenseService).deleteById(3L);
        }

        @Test
        @DisplayName("onCreate: невалидный JSON → kafkaErrors++")
        void onCreate_invalidJson() {
            licenseConsumer.onCreate("BROKEN");
            verify(err).increment();
        }
    }

    // ── ErrorLogsConsumer ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ErrorLogsConsumer")
    class ErrorLogs {

        @Test
        @DisplayName("handleErrorLog → не бросает исключений")
        void handleErrorLog_noException() {
            Assertions.assertThatCode(() ->
                    errorConsumer.handleErrorLog("{\"error\":\"NullPointerException\"}")
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("handleErrorLog: null message → не бросает")
        void handleErrorLog_null_noException() {
            Assertions.assertThatCode(() -> errorConsumer.handleErrorLog(null))
                    .doesNotThrowAnyException();
        }
    }
}
