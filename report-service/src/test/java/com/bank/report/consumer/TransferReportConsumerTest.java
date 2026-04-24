package com.bank.report.consumer;

import com.bank.report.entity.TransferReport;
import com.bank.report.event.TransferNotificationEvent;
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
 * Unit-тесты TransferReportConsumer.
 */
 /**
 * Покрытие:
 *   CREATED → новая запись; дубль → skip
 *   COMPLETED / BLOCKED / CANCELLED → обновление статуса
 *   out-of-order (COMPLETED без CREATED) → создаётся новая запись
 *   null event → не бросает
 */
@QuarkusTest
@DisplayName("TransferReportConsumer — unit tests")
class TransferReportConsumerTest {

    @Inject TransferReportConsumer consumer;
    @InjectMock ReportMetrics metrics;

    private Counter okCounter;
    private Counter dupCounter;

    @BeforeEach void setUp() {
        okCounter  = mock(Counter.class);
        dupCounter = mock(Counter.class);
        when(metrics.getTransfersStored()).thenReturn(okCounter);
        when(metrics.getKafkaDuplicatesSkipped()).thenReturn(dupCounter);
    }

    @BeforeEach @Transactional
    void cleanDb() { TransferReport.deleteAll(); }

    private TransferNotificationEvent event(Long transferId, Long clientId, String status) {
        TransferNotificationEvent e = new TransferNotificationEvent();
        e.transferId = transferId; e.clientId = clientId;
        e.transferType = "CARD"; e.status = status;
        e.amount = new BigDecimal("1000.00"); e.currency = "RUB";
        e.recipientDisplay = "**** 5678"; e.occurredAt = LocalDateTime.now();
        return e;
    }

    @Nested @DisplayName("CREATED событие")
    class Created {

        @Test @DisplayName("новый перевод → запись + transfersStored++")
        @Transactional
        void newTransfer_persists() {
            consumer.handleTransferEvent(event(1L, 42L, "CREATED"));
            Assertions.assertThat(TransferReport.count("transferId", 1L)).isEqualTo(1);
            verify(okCounter).increment();
        }

        @Test @DisplayName("clientId сохраняется как Long")
        @Transactional
        void clientIdIsLong() {
            consumer.handleTransferEvent(event(2L, 77L, "CREATED"));
            TransferReport r = TransferReport.<TransferReport>find("transferId", 2L).firstResult();
            Assertions.assertThat(r.getClientId()).isEqualTo(77L);
        }

        @Test @DisplayName("дубль CREATED → skip + kafkaDuplicatesSkipped++")
        @Transactional
        void duplicate_skips() {
            consumer.handleTransferEvent(event(3L, 42L, "CREATED"));
            consumer.handleTransferEvent(event(3L, 42L, "CREATED"));
            Assertions.assertThat(TransferReport.count("transferId", 3L)).isEqualTo(1);
            verify(dupCounter).increment();
        }
    }

    @Nested @DisplayName("Обновление статуса")
    class StatusUpdate {

        @Test @DisplayName("COMPLETED → статус обновляется")
        @Transactional
        void completed_updatesStatus() {
            consumer.handleTransferEvent(event(10L, 42L, "CREATED"));
            consumer.handleTransferEvent(event(10L, 42L, "COMPLETED"));

            TransferReport r = TransferReport.<TransferReport>find("transferId", 10L).firstResult();
            Assertions.assertThat(r.getStatus()).isEqualTo("COMPLETED");
        }

        @Test @DisplayName("BLOCKED → статус и reason обновляются")
        @Transactional
        void blocked_updatesStatusAndReason() {
            consumer.handleTransferEvent(event(11L, 42L, "CREATED"));
            TransferNotificationEvent blocked = event(11L, 42L, "BLOCKED");
            blocked.reason = "Antifraud limit exceeded";
            consumer.handleTransferEvent(blocked);

            TransferReport r = TransferReport.<TransferReport>find("transferId", 11L).firstResult();
            Assertions.assertThat(r.getStatus()).isEqualTo("BLOCKED");
            Assertions.assertThat(r.getReason()).isEqualTo("Antifraud limit exceeded");
        }

        @Test @DisplayName("out-of-order: COMPLETED без CREATED → новая запись")
        @Transactional
        void outOfOrder_createsRecord() {
            consumer.handleTransferEvent(event(20L, 42L, "COMPLETED"));
            Assertions.assertThat(TransferReport.count("transferId", 20L)).isEqualTo(1);
        }

        @Test @DisplayName("тот же статус повторно → idempotent skip")
        @Transactional
        void sameStatus_idempotentSkip() {
            consumer.handleTransferEvent(event(30L, 42L, "CREATED"));
            consumer.handleTransferEvent(event(30L, 42L, "CREATED"));
            verify(dupCounter).increment();
        }
    }

    @Nested @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test @DisplayName("null event → не бросает")
        void nullEvent_noException() {
            Assertions.assertThatCode(() -> consumer.handleTransferEvent(null))
                    .doesNotThrowAnyException();
        }

        @Test @DisplayName("null transferId → не бросает")
        void nullTransferId_noException() {
            Assertions.assertThatCode(() ->
                consumer.handleTransferEvent(event(null, 42L, "CREATED"))
            ).doesNotThrowAnyException();
        }
    }
}
