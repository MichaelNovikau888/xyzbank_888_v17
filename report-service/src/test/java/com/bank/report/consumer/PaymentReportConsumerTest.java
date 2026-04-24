package com.bank.report.consumer;

import com.bank.report.entity.PaymentReport;
import com.bank.report.event.PaymentCreatedEvent;
import com.bank.report.event.PaymentStatusChangedEvent;
import com.bank.report.metrics.ReportMetrics;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

/**
 * Unit-тесты PaymentReportConsumer.
 */
 /**
 * Покрытие:
 *   handlePaymentCreated: новый → persist; дубль → skip; null → skip
 *   handlePaymentStatusChanged: найден → обновляет; не найден → skip;
 *                               тот же статус → skip (idempotent)
 */
@QuarkusTest
@DisplayName("PaymentReportConsumer — unit tests")
class PaymentReportConsumerTest {

    @Inject PaymentReportConsumer consumer;
    @InjectMock ReportMetrics metrics;

    private Counter okCounter;
    private Counter dupCounter;

    @BeforeEach
    void setUp() {
        okCounter  = mock(Counter.class);
        dupCounter = mock(Counter.class);
        when(metrics.getPaymentsStored()).thenReturn(okCounter);
        when(metrics.getKafkaDuplicatesSkipped()).thenReturn(dupCounter);
    }

    @BeforeEach @Transactional
    void cleanDb() { PaymentReport.deleteAll(); }

    private PaymentCreatedEvent createdEvent(Long paymentId, Long clientId) {
        PaymentCreatedEvent e = new PaymentCreatedEvent();
        e.paymentId = paymentId; e.clientId = clientId;
        e.amount = new BigDecimal("500.00"); e.currency = "RUB";
        e.status = "CREATED"; e.recipientAccount = "40817810099910004312";
        e.createdAt = LocalDateTime.now();
        return e;
    }

    private PaymentStatusChangedEvent changedEvent(Long paymentId, String oldS, String newS) {
        PaymentStatusChangedEvent e = new PaymentStatusChangedEvent();
        e.paymentId = paymentId; e.oldStatus = oldS; e.newStatus = newS;
        return e;
    }

    // ── handlePaymentCreated ──────────────────────────────────────────────────

    @Nested @DisplayName("handlePaymentCreated")
    class HandleCreated {

        @Test @DisplayName("новое событие → запись в БД + paymentsStored++")
        @Transactional
        void newEvent_persists() {
            consumer.handlePaymentCreated(createdEvent(1L, 42L));

            Assertions.assertThat(PaymentReport.count("paymentId", 1L)).isEqualTo(1);
            verify(okCounter).increment();
        }

        @Test @DisplayName("clientId сохраняется как Long = Profile.id")
        @Transactional
        void clientIdStoredAsLong() {
            consumer.handlePaymentCreated(createdEvent(2L, 99L));

            PaymentReport saved = PaymentReport.<PaymentReport>find("paymentId", 2L).firstResult();
            Assertions.assertThat(saved).isNotNull();
            Assertions.assertThat(saved.getClientId()).isEqualTo(99L);
        }

        @Test @DisplayName("дублирующий paymentId → skip + kafkaDuplicatesSkipped++")
        @Transactional
        void duplicate_skips() {
            consumer.handlePaymentCreated(createdEvent(3L, 42L));
            consumer.handlePaymentCreated(createdEvent(3L, 42L));

            Assertions.assertThat(PaymentReport.count("paymentId", 3L)).isEqualTo(1);
            verify(dupCounter).increment();
        }

        @Test @DisplayName("null event → не бросает исключений")
        void nullEvent_noException() {
            Assertions.assertThatCode(() -> consumer.handlePaymentCreated(null))
                    .doesNotThrowAnyException();
            verify(okCounter, never()).increment();
        }

        @Test @DisplayName("null paymentId → не бросает исключений")
        void nullPaymentId_noException() {
            PaymentCreatedEvent e = createdEvent(null, 42L);
            Assertions.assertThatCode(() -> consumer.handlePaymentCreated(e))
                    .doesNotThrowAnyException();
        }
    }

    // ── handlePaymentStatusChanged ────────────────────────────────────────────

    @Nested @DisplayName("handlePaymentStatusChanged")
    class HandleStatusChanged {

        @Test @DisplayName("найден → статус обновляется")
        @Transactional
        void found_updatesStatus() {
            consumer.handlePaymentCreated(createdEvent(10L, 42L));
            consumer.handlePaymentStatusChanged(changedEvent(10L, "CREATED", "COMPLETED"));

            PaymentReport r = PaymentReport.<PaymentReport>find("paymentId", 10L).firstResult();
            Assertions.assertThat(r.getStatus()).isEqualTo("COMPLETED");
        }

        @Test @DisplayName("не найден → skip, без исключений")
        void notFound_skips() {
            Assertions.assertThatCode(() ->
                consumer.handlePaymentStatusChanged(changedEvent(999L, "CREATED", "COMPLETED"))
            ).doesNotThrowAnyException();
        }

        @Test @DisplayName("тот же статус → idempotent skip + kafkaDuplicatesSkipped++")
        @Transactional
        void sameStatus_idempotentSkip() {
            consumer.handlePaymentCreated(createdEvent(20L, 42L));
            consumer.handlePaymentStatusChanged(changedEvent(20L, "CREATED", "CREATED"));
            verify(dupCounter).increment();
        }

        @Test @DisplayName("null event → не бросает")
        void nullEvent_noException() {
            Assertions.assertThatCode(() -> consumer.handlePaymentStatusChanged(null))
                    .doesNotThrowAnyException();
        }
    }
}
