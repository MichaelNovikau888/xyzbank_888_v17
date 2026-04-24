package com.bank.publicinfo.consumer;

import com.bank.publicinfo.metrics.PublicInfoMetrics;
import com.bank.publicinfo.service.BankDetailsService;
import com.bank.publicinfo.service.BranchService;
import com.bank.publicinfo.service.ATMService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты Kafka-консьюмеров public-info-service.
 /
 * Тестируем:
 *   - корректный JSON → сервис вызывается, метрика инкрементируется
 *   - невалидный JSON → метрика ошибок инкрементируется, исключение НЕ пробрасывается
 *   - null id в delete → команда игнорируется без вызова сервиса
 */
@QuarkusTest
class PublicInfoConsumersTest {

    @InjectMock BankDetailsService bankService;
    @InjectMock BranchService      branchService;
    @InjectMock ATMService         atmService;
    @InjectMock PublicInfoMetrics  metrics;

    @Inject BankDetailsConsumer bankConsumer;
    @Inject BranchConsumer      branchConsumer;
    @Inject ATMConsumer         atmConsumer;

    private Counter mockCounter;

    @BeforeEach
    void setUp() {
        mockCounter = mock(Counter.class);
        when(metrics.getBankDetailsCreated()).thenReturn(mockCounter);
        when(metrics.getBankDetailsUpdated()).thenReturn(mockCounter);
        when(metrics.getBankDetailsDeleted()).thenReturn(mockCounter);
        when(metrics.getBranchCreated()).thenReturn(mockCounter);
        when(metrics.getBranchUpdated()).thenReturn(mockCounter);
        when(metrics.getBranchDeleted()).thenReturn(mockCounter);
        when(metrics.getAtmCreated()).thenReturn(mockCounter);
        when(metrics.getAtmUpdated()).thenReturn(mockCounter);
        when(metrics.getAtmDeleted()).thenReturn(mockCounter);
        when(metrics.getKafkaErrors()).thenReturn(mockCounter);
    }

    // ── BankDetailsConsumer ───────────────────────────────────────────────────

    @Test
    @DisplayName("bank-create: валидный JSON → сервис вызывается, счётчик ++")
    void bankCreate_valid_callsServiceAndIncrements() {
        String msg = "{\"bik\":44525225,\"inn\":7710140679,\"kpp\":771001001," +
                     "\"corAccount\":30101810400000000225,\"city\":\"Москва\"," +
                     "\"jointStockCompany\":\"ПАО\",\"name\":\"Сбербанк\"}";
        bankConsumer.onCreate(msg);
        verify(bankService).create(any());
        verify(metrics.getBankDetailsCreated()).increment();
    }

    @Test
    @DisplayName("bank-create: невалидный JSON → счётчик ошибок ++, исключение не бросается")
    void bankCreate_invalidJson_incrementsErrorAndDoesNotThrow() {
        assertThatCode(() -> bankConsumer.onCreate("NOT_JSON"))
                .doesNotThrowAnyException();
        verify(bankService, never()).create(any());
        verify(metrics.getKafkaErrors()).increment();
    }

    @Test
    @DisplayName("bank-update: валидный JSON с id → сервис update вызывается")
    void bankUpdate_valid_callsService() {
        String msg = "{\"id\":1,\"bik\":44525225,\"inn\":7710140679,\"kpp\":771001001," +
                     "\"corAccount\":30101810400000000225,\"city\":\"Москва\"," +
                     "\"jointStockCompany\":\"ПАО\",\"name\":\"Банк\"}";
        bankConsumer.onUpdate(msg);
        verify(bankService).update(any());
        verify(metrics.getBankDetailsUpdated()).increment();
    }

    @Test
    @DisplayName("bank-delete: id=null → сервис НЕ вызывается")
    void bankDelete_nullId_skips() {
        bankConsumer.onDelete("{\"city\":\"Москва\"}");
        verify(bankService, never()).deleteById(any());
    }

    @Test
    @DisplayName("bank-delete: валидный JSON с id → сервис deleteById вызывается")
    void bankDelete_valid_callsService() {
        bankConsumer.onDelete("{\"id\":5}");
        verify(bankService).deleteById(5L);
        verify(metrics.getBankDetailsDeleted()).increment();
    }

    // ── BranchConsumer ────────────────────────────────────────────────────────

    @Test
    @DisplayName("branch-create: валидный JSON → сервис вызывается")
    void branchCreate_valid_callsService() {
        String msg = "{\"address\":\"ул. Ленина, 1\",\"phoneNumber\":74951234567," +
                     "\"city\":\"Москва\",\"startOfWork\":\"09:00:00\",\"endOfWork\":\"18:00:00\"}";
        branchConsumer.onCreate(msg);
        verify(branchService).create(any());
        verify(metrics.getBranchCreated()).increment();
    }

    @Test
    @DisplayName("branch-update: id=null → сервис НЕ вызывается")
    void branchUpdate_nullId_skips() {
        branchConsumer.onUpdate("{\"city\":\"Москва\"}");
        verify(branchService, never()).update(any(), any());
    }

    @Test
    @DisplayName("branch-delete: невалидный JSON → ошибка, без throw")
    void branchDelete_invalidJson_doesNotThrow() {
        assertThatCode(() -> branchConsumer.onDelete("INVALID"))
                .doesNotThrowAnyException();
        verify(metrics.getKafkaErrors()).increment();
    }

    // ── ATMConsumer ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("atm-create: валидный JSON → сервис вызывается")
    void atmCreate_valid_callsService() {
        String msg = "{\"address\":\"ул. Мира, 1\",\"allHours\":true,\"branchId\":1}";
        atmConsumer.onCreate(msg);
        verify(atmService).create(any());
        verify(metrics.getAtmCreated()).increment();
    }

    @Test
    @DisplayName("atm-delete: id=null → сервис НЕ вызывается")
    void atmDelete_nullId_skips() {
        atmConsumer.onDelete("{\"address\":\"ул. Мира, 1\"}");
        verify(atmService, never()).deleteById(any());
    }

    @Test
    @DisplayName("atm-update: невалидный JSON → ошибка, без throw")
    void atmUpdate_invalidJson_doesNotThrow() {
        assertThatCode(() -> atmConsumer.onUpdate("BAD"))
                .doesNotThrowAnyException();
        verify(metrics.getKafkaErrors()).increment();
    }
}
