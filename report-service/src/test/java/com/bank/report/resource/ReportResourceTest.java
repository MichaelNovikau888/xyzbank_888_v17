package com.bank.report.resource;

import com.bank.report.consumer.PaymentReportConsumer;
import com.bank.report.consumer.TransferReportConsumer;
import com.bank.report.entity.PaymentReport;
import com.bank.report.entity.TransferReport;
import com.bank.report.event.PaymentCreatedEvent;
import com.bank.report.event.TransferNotificationEvent;
import com.bank.report.metrics.ReportMetrics;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты ReportResource (H2 in-memory).
 */
 /**
 * Покрытие:
 *   Клиентский вид: payments/day, transfers/day, summary day/week/month
 *   Бухгалтерский вид: payments/day, transfers/day, summary day/week/month, CSV
 *   Изоляция: клиент A не видит данные клиента B
 *   Валидация: невалидный clientId
 *   Health & Metrics
 */
@QuarkusTest
@DisplayName("ReportResource — integration tests")
class ReportResourceTest {

    @Inject PaymentReportConsumer  paymentConsumer;
    @Inject TransferReportConsumer transferConsumer;
    @InjectMock ReportMetrics metrics;

    private static final Long   CLIENT_A = 42L;
    private static final Long   CLIENT_B = 99L;
    private static final String TODAY    = LocalDate.now().toString();

    @BeforeEach
    void mockMetrics() {
        Counter c = mock(Counter.class);
        when(metrics.getPaymentsStored()).thenReturn(c);
        when(metrics.getTransfersStored()).thenReturn(c);
        when(metrics.getKafkaDuplicatesSkipped()).thenReturn(c);
        when(metrics.getCsvReportsGenerated()).thenReturn(c);
        when(metrics.getCsvReportsFailed()).thenReturn(c);
        when(metrics.getCsvGenerationTimer()).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
    }

    @BeforeEach @Transactional
    void cleanDb() { PaymentReport.deleteAll(); TransferReport.deleteAll(); }

    @Transactional
    void persistPayment(Long paymentId, Long clientId, BigDecimal amount) {
        PaymentCreatedEvent e = new PaymentCreatedEvent();
        e.paymentId = paymentId; e.clientId = clientId;
        e.amount = amount; e.currency = "RUB"; e.status = "COMPLETED";
        e.recipientAccount = "40817810099910004312";
        e.createdAt = LocalDateTime.now();
        paymentConsumer.handlePaymentCreated(e);
    }

    @Transactional
    void persistTransfer(Long transferId, Long clientId, BigDecimal amount) {
        TransferNotificationEvent e = new TransferNotificationEvent();
        e.transferId = transferId; e.clientId = clientId;
        e.transferType = "CARD"; e.status = "COMPLETED";
        e.amount = amount; e.currency = "RUB";
        e.occurredAt = LocalDateTime.now();
        transferConsumer.handleTransferEvent(e);
    }

    // ── Клиентский вид ────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /client/{id}/payments/day")
    class ClientPaymentsDay {

        @Test @DisplayName("200 с данными клиента A")
        void returnsClientData() {
            persistPayment(1L, CLIENT_A, new BigDecimal("500"));
            persistPayment(2L, CLIENT_B, new BigDecimal("300"));

            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/payments/day?date=" + TODAY)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].paymentId", equalTo(1));
        }

        @Test @DisplayName("клиент B не видит платежи клиента A")
        void clientIsolation() {
            persistPayment(3L, CLIENT_A, new BigDecimal("1000"));

            given().when()
                .get("/api/v1/reports/client/" + CLIENT_B + "/payments/day?date=" + TODAY)
                .then().statusCode(200)
                .body("$", hasSize(0));
        }

        @Test @DisplayName("пустая БД → 200 пустой список")
        void emptyDb_emptyList() {
            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/payments/day?date=" + TODAY)
                .then().statusCode(200)
                .body("$", hasSize(0));
        }

        @Test @DisplayName("невалидный clientId (0) → 500 (IllegalArgumentException)")
        void invalidClientId_error() {
            given().when()
                .get("/api/v1/reports/client/0/payments/day?date=" + TODAY)
                .then().statusCode(greaterThanOrEqualTo(400));
        }
    }

    @Nested @DisplayName("GET /client/{id}/transfers/day")
    class ClientTransfersDay {

        @Test @DisplayName("возвращает только переводы клиента")
        void returnsOnlyClientTransfers() {
            persistTransfer(10L, CLIENT_A, new BigDecimal("2000"));
            persistTransfer(11L, CLIENT_B, new BigDecimal("3000"));

            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/transfers/day?date=" + TODAY)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].transferId", equalTo(10));
        }
    }

    @Nested @DisplayName("GET /client/{id}/summary/day")
    class ClientSummaryDay {

        @Test @DisplayName("200 с правильными счётчиками")
        void correctSummary() {
            persistPayment(20L, CLIENT_A, new BigDecimal("100"));
            persistPayment(21L, CLIENT_A, new BigDecimal("200"));
            persistTransfer(30L, CLIENT_A, new BigDecimal("500"));

            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/summary/day?date=" + TODAY)
                .then().statusCode(200)
                .body("paymentCount", equalTo(2))
                .body("transferCount", equalTo(1))
                .body("totalPaymentAmount", equalTo(300.0f));
        }
    }

    @Nested @DisplayName("GET /client/{id}/summary/week")
    class ClientSummaryWeek {

        @Test @DisplayName("200 с периодом в формате YYYY-Www")
        void weekPeriodFormat() {
            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/summary/week?date=" + TODAY)
                .then().statusCode(200)
                .body("period", containsString("-W"))
                .body("paymentCount", greaterThanOrEqualTo(0));
        }
    }

    @Nested @DisplayName("GET /client/{id}/summary/month")
    class ClientSummaryMonth {

        @Test @DisplayName("200 с периодом в формате YYYY-MM")
        void monthPeriodFormat() {
            given().when()
                .get("/api/v1/reports/client/" + CLIENT_A + "/summary/month?date=" + TODAY)
                .then().statusCode(200)
                .body("period", matchesPattern("\\d{4}-\\d{2}"))
                .body("paymentCount", greaterThanOrEqualTo(0));
        }
    }

    // ── Бухгалтерский вид ─────────────────────────────────────────────────────

    @Nested @DisplayName("GET /bank/payments/day")
    class BankPaymentsDay {

        @Test @DisplayName("включает данные всех клиентов")
        void includesAllClients() {
            persistPayment(40L, CLIENT_A, new BigDecimal("100"));
            persistPayment(41L, CLIENT_B, new BigDecimal("200"));

            given().when()
                .get("/api/v1/reports/bank/payments/day?date=" + TODAY)
                .then().statusCode(200)
                .body("$", hasSize(2));
        }
    }

    @Nested @DisplayName("GET /bank/summary/day")
    class BankSummaryDay {

        @Test @DisplayName("сводка включает все платежи и переводы")
        void includesAllData() {
            persistPayment(50L, CLIENT_A, new BigDecimal("100"));
            persistPayment(51L, CLIENT_B, new BigDecimal("200"));
            persistTransfer(60L, CLIENT_A, new BigDecimal("300"));

            given().when()
                .get("/api/v1/reports/bank/summary/day?date=" + TODAY)
                .then().statusCode(200)
                .body("paymentCount", equalTo(2))
                .body("transferCount", equalTo(1))
                .body("totalPaymentAmount", equalTo(300.0f))
                .body("totalTransferAmount", equalTo(300.0f));
        }
    }

    @Nested @DisplayName("GET /bank/daily (CSV)")
    class BankDailyCsv {

        @Test @DisplayName("200 с Content-Type text/csv")
        void returnsCsvContentType() {
            given().when()
                .get("/api/v1/reports/bank/daily?date=" + TODAY)
                .then().statusCode(200)
                .contentType(containsString("text/csv"));
        }

        @Test @DisplayName("CSV содержит заголовок отчёта")
        void csvContainsHeader() {
            given().when()
                .get("/api/v1/reports/bank/daily?date=" + TODAY)
                .then().statusCode(200)
                .body(containsString("ОТЧЁТ ЗА"));
        }

        @Test @DisplayName("CSV содержит секции ПЛАТЕЖИ и ПЕРЕВОДЫ")
        void csvContainsBothSections() {
            given().when()
                .get("/api/v1/reports/bank/daily?date=" + TODAY)
                .then().statusCode(200)
                .body(containsString("ПЛАТЕЖИ"))
                .body(containsString("ПЕРЕВОДЫ"));
        }
    }

    @Nested @DisplayName("GET /bank/partitions/health")
    class PartitionHealth {

        @Test @DisplayName("200 с текстовым ответом")
        void returns200() {
            given().when()
                .get("/api/v1/reports/bank/partitions/health")
                .then().statusCode(200);
        }
    }

        // ── Health & Metrics ──────────────────────────────────────────────────────

    @Nested @DisplayName("Health & Metrics")
    class HealthAndMetrics {

        @Test @DisplayName("GET /q/health → 200 UP")
        void health_up() {
            given().when().get("/q/health")
                .then().statusCode(200).body("status", equalTo("UP"));
        }

        @Test @DisplayName("GET /q/metrics → содержит report метрики")
        void metrics_exposed() {
            given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("report_payments_stored_total"))
                .body(containsString("report_transfers_stored_total"))
                .body(containsString("report_csv_generated_total"));
        }
    }
}
