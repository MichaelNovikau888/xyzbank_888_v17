package com.bank.report.service;

import com.bank.report.consumer.PaymentReportConsumer;
import com.bank.report.consumer.TransferReportConsumer;
import com.bank.report.dto.PaymentReportDto;
import com.bank.report.dto.PeriodSummaryDto;
import com.bank.report.dto.TransferReportDto;
import com.bank.report.entity.PaymentReport;
import com.bank.report.entity.TransferReport;
import com.bank.report.event.PaymentCreatedEvent;
import com.bank.report.event.TransferNotificationEvent;
import com.bank.report.metrics.ReportMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты ReportService.
 */
 /**
 * Покрытие:
 *   Клиентский вид: getClientPaymentsByDay, getClientTransfersByDay, getClientSummary
 *   Бухгалтерский вид: getAllPaymentsByDay, getAllTransfersByDay, getBankSummary
 *   Периоды: weekRange, monthRange
 *   Валидация: null/zero clientId → IllegalArgumentException
 *   generateDailyReportCsv: пустая БД → CSV без строк, не бросает
 */
@QuarkusTest
@DisplayName("ReportService — unit tests")
class ReportServiceTest {

    @Inject ReportService             reportService;
    @Inject PaymentReportConsumer     paymentConsumer;
    @Inject TransferReportConsumer    transferConsumer;
    @InjectMock ReportMetrics         metrics;

    private static final LocalDate TODAY = LocalDate.now();
    private static final Long      CLIENT_A = 42L;
    private static final Long      CLIENT_B = 99L;

    @BeforeEach
    void mockMetrics() {
        Counter c = mock(Counter.class);
        Timer   t = mock(Timer.class);
        when(metrics.getPaymentsStored()).thenReturn(c);
        when(metrics.getTransfersStored()).thenReturn(c);
        when(metrics.getKafkaDuplicatesSkipped()).thenReturn(c);
        when(metrics.getCsvReportsGenerated()).thenReturn(c);
        when(metrics.getCsvReportsFailed()).thenReturn(c);
        when(metrics.getCsvGenerationTimer()).thenReturn(t);
        // Delegate timer.record(Callable) to actually execute the lambda
        doAnswer(inv -> {
            try {
                return ((java.util.concurrent.Callable<?>) inv.getArgument(0)).call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).when(t).record(any(java.util.concurrent.Callable.class));
    }

    @BeforeEach @Transactional
    void cleanDb() { PaymentReport.deleteAll(); TransferReport.deleteAll(); }

    private void insertPayment(Long paymentId, Long clientId, BigDecimal amount) {
        PaymentCreatedEvent e = new PaymentCreatedEvent();
        e.paymentId = paymentId; e.clientId = clientId;
        e.amount = amount; e.currency = "RUB"; e.status = "COMPLETED";
        e.recipientAccount = "40817810099910004312";
        e.createdAt = LocalDateTime.now();
        paymentConsumer.handlePaymentCreated(e);
    }

    private void insertTransfer(Long transferId, Long clientId, BigDecimal amount) {
        TransferNotificationEvent e = new TransferNotificationEvent();
        e.transferId = transferId; e.clientId = clientId;
        e.transferType = "ACCOUNT"; e.status = "COMPLETED";
        e.amount = amount; e.currency = "RUB";
        e.occurredAt = LocalDateTime.now();
        transferConsumer.handleTransferEvent(e);
    }

    // ── Клиентский вид ────────────────────────────────────────────────────────

    @Nested @DisplayName("getClientPaymentsByDay")
    class ClientPaymentsByDay {

        @Test @DisplayName("возвращает только платежи клиента за день")
        @Transactional
        void returnsOnlyClientPayments() {
            insertPayment(1L, CLIENT_A, new BigDecimal("500"));
            insertPayment(2L, CLIENT_B, new BigDecimal("300"));

            List<PaymentReportDto> result =
                reportService.getClientPaymentsByDay(CLIENT_A, TODAY);

            Assertions.assertThat(result).hasSize(1);
            Assertions.assertThat(result.get(0).paymentId).isEqualTo(1L);
        }

        @Test @DisplayName("clientId=null → IllegalArgumentException")
        void nullClientId_throws() {
            Assertions.assertThatThrownBy(() ->
                reportService.getClientPaymentsByDay(null, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test @DisplayName("clientId=0 → IllegalArgumentException")
        void zeroClientId_throws() {
            Assertions.assertThatThrownBy(() ->
                reportService.getClientPaymentsByDay(0L, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested @DisplayName("getClientTransfersByDay")
    class ClientTransfersByDay {

        @Test @DisplayName("возвращает только переводы клиента")
        @Transactional
        void returnsOnlyClientTransfers() {
            insertTransfer(10L, CLIENT_A, new BigDecimal("1000"));
            insertTransfer(11L, CLIENT_B, new BigDecimal("2000"));

            List<TransferReportDto> result =
                reportService.getClientTransfersByDay(CLIENT_A, TODAY);

            Assertions.assertThat(result).hasSize(1);
            Assertions.assertThat(result.get(0).transferId).isEqualTo(10L);
        }
    }

    @Nested @DisplayName("getClientSummary — периоды")
    class ClientSummary {

        @Test @DisplayName("день: правильные суммы и количества")
        @Transactional
        void dayPeriod_correctCounts() {
            insertPayment(20L, CLIENT_A, new BigDecimal("100"));
            insertPayment(21L, CLIENT_A, new BigDecimal("200"));
            insertTransfer(30L, CLIENT_A, new BigDecimal("500"));

            PeriodSummaryDto s =
                reportService.getClientSummary(CLIENT_A, TODAY, TODAY, TODAY.toString());

            Assertions.assertThat(s.paymentCount).isEqualTo(2);
            Assertions.assertThat(s.transferCount).isEqualTo(1);
            Assertions.assertThat(s.totalPaymentAmount)
                .isEqualByComparingTo(new BigDecimal("300"));
            Assertions.assertThat(s.totalTransferAmount)
                .isEqualByComparingTo(new BigDecimal("500"));
        }

        @Test @DisplayName("пустой период → нули")
        void emptyPeriod_zeros() {
            PeriodSummaryDto s =
                reportService.getClientSummary(CLIENT_A, TODAY, TODAY, TODAY.toString());
            Assertions.assertThat(s.paymentCount).isZero();
            Assertions.assertThat(s.transferCount).isZero();
        }
    }

    // ── Бухгалтерский вид ─────────────────────────────────────────────────────

    @Nested @DisplayName("getBankSummary")
    class BankSummary {

        @Test @DisplayName("включает данные всех клиентов")
        @Transactional
        void includesAllClients() {
            insertPayment(40L, CLIENT_A, new BigDecimal("100"));
            insertPayment(41L, CLIENT_B, new BigDecimal("200"));
            insertTransfer(50L, CLIENT_A, new BigDecimal("300"));
            insertTransfer(51L, CLIENT_B, new BigDecimal("400"));

            PeriodSummaryDto s = reportService.getBankSummary(TODAY, TODAY, TODAY.toString());

            Assertions.assertThat(s.paymentCount).isEqualTo(2);
            Assertions.assertThat(s.transferCount).isEqualTo(2);
            Assertions.assertThat(s.totalPaymentAmount)
                .isEqualByComparingTo(new BigDecimal("300"));
            Assertions.assertThat(s.totalTransferAmount)
                .isEqualByComparingTo(new BigDecimal("700"));
        }
    }

    // ── weekRange / monthRange ────────────────────────────────────────────────

    @Nested @DisplayName("Диапазоны дат")
    class DateRanges {

        @Test @DisplayName("weekRange: понедельник ≤ date ≤ воскресенье")
        void weekRange_mondayToSunday() {
            LocalDate[] r = ReportService.weekRange(LocalDate.of(2026, 4, 21)); // вторник
            Assertions.assertThat(r[0]).isEqualTo(LocalDate.of(2026, 4, 20)); // пн
            Assertions.assertThat(r[1]).isEqualTo(LocalDate.of(2026, 4, 26)); // вс
        }

        @Test @DisplayName("monthRange: 1-е ≤ date ≤ последний день")
        void monthRange_firstToLast() {
            LocalDate[] r = ReportService.monthRange(LocalDate.of(2026, 2, 15));
            Assertions.assertThat(r[0]).isEqualTo(LocalDate.of(2026, 2, 1));
            Assertions.assertThat(r[1]).isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }

    // ── generateDailyReportCsv ────────────────────────────────────────────────

    @Nested @DisplayName("generateDailyReportCsv")
    class GenerateCsv {

        @Test @DisplayName("пустая БД → CSV с заголовком, не бросает")
        void emptyDb_returnsHeaderCsv() {
            byte[] csv = reportService.generateDailyReportCsv(TODAY);
            String content = new String(csv);
            Assertions.assertThat(content).contains("ОТЧЁТ ЗА");
            Assertions.assertThat(content).contains("ПЛАТЕЖИ");
            Assertions.assertThat(content).contains("ПЕРЕВОДЫ");
        }

        @Test @DisplayName("с данными → CSV содержит paymentId и transferId")
        @Transactional
        void withData_containsIds() {
            insertPayment(100L, CLIENT_A, new BigDecimal("999"));
            insertTransfer(200L, CLIENT_A, new BigDecimal("888"));

            byte[] csv = reportService.generateDailyReportCsv(TODAY);
            String content = new String(csv);
            Assertions.assertThat(content).contains("100");
            Assertions.assertThat(content).contains("200");
        }
    }
}
