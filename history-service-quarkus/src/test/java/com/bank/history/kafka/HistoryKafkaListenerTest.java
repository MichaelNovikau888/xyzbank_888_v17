package com.bank.history.kafka;

import com.bank.history.entity.History;
import com.bank.history.metrics.HistoryMetrics;
import com.bank.history.service.HistoryService;
import io.micrometer.core.instrument.Counter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты HistoryKafkaListener.
 */
 /**
 * Тестируем логику каждого обработчика напрямую (без Kafka-брокера).
 * HistoryService и HistoryMetrics — моки.
 */
 /**
 * Покрываемые сценарии:
 *   - Идемпотентность через content-hash (новое / дубль / дважды одно)
 *   - Правильные serviceName и eventType для каждого топика
 *   - contentHash не пустой и детерминирован
 *   - Все 6 handler-методов: auditLog, transferEvent, accountEvent,
 *     errorLog, auditLogDlq, errorLogDlq
 *   - DLQ-счётчик инкрементируется только для DLQ-handlers
 */
@QuarkusTest
@DisplayName("HistoryKafkaListener — unit tests")
class HistoryKafkaListenerTest {

    @InjectMock HistoryService  historyService;
    @InjectMock HistoryMetrics  historyMetrics;

    @Inject HistoryKafkaListener listener;

    private Counter savedCounter;
    private Counter skippedCounter;
    private Counter dlqCounter;

    @BeforeEach
    void setUp() {
        savedCounter   = mock(Counter.class);
        skippedCounter = mock(Counter.class);
        dlqCounter     = mock(Counter.class);
        Counter genericCounter = mock(Counter.class);

        when(historyMetrics.getEventsSaved()).thenReturn(savedCounter);
        when(historyMetrics.getEventsIdempotentSkipped()).thenReturn(skippedCounter);
        when(historyMetrics.getDlqEventsReceived()).thenReturn(dlqCounter);
        when(historyMetrics.getAuditLogsReceived()).thenReturn(genericCounter);
        when(historyMetrics.getTransferEventsReceived()).thenReturn(genericCounter);
        when(historyMetrics.getAccountEventsReceived()).thenReturn(genericCounter);
        when(historyMetrics.getErrorLogsReceived()).thenReturn(genericCounter);
    }

    // ── handleAuditLog ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAuditLog")
    class HandleAuditLog {

        @Test
        @DisplayName("новое событие → saveHistory вызывается, saved++")
        void newEvent_savesAndIncrements() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLog("{\"event\":\"audit\",\"id\":1}");

            verify(historyService).saveHistory(any(History.class));
            verify(savedCounter).increment();
        }

        @Test
        @DisplayName("дубль → saveHistory НЕ вызывается, skipped++")
        void duplicate_skipsAndIncrements() {
            when(historyService.existsByContentHash(anyString())).thenReturn(true);

            listener.handleAuditLog("{\"event\":\"audit\",\"id\":1}");

            verify(historyService, never()).saveHistory(any());
            verify(skippedCounter).increment();
        }

        @Test
        @DisplayName("serviceName=audit.logs, eventType=AUDIT")
        void setsCorrectServiceNameAndType() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLog("{\"event\":\"audit\"}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("audit.logs");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("AUDIT");
        }

        @Test
        @DisplayName("contentHash не null и не пустой")
        void contentHashIsSet() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLog("{\"data\":\"x\"}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getContentHash())
                    .isNotNull()
                    .hasSize(64); // SHA-256 hex = 64 символа
        }

        @Test
        @DisplayName("один и тот же payload → одинаковый contentHash (детерминизм)")
        void samePayload_sameHash() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);
            String payload = "{\"event\":\"audit\",\"id\":42}";

            listener.handleAuditLog(payload);
            listener.handleAuditLog(payload);

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService, times(2)).saveHistory(cap.capture());
            String hash1 = cap.getAllValues().get(0).getContentHash();
            String hash2 = cap.getAllValues().get(1).getContentHash();
            Assertions.assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("разные payload → разные contentHash")
        void differentPayloads_differentHashes() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLog("{\"id\":1}");
            listener.handleAuditLog("{\"id\":2}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService, times(2)).saveHistory(cap.capture());
            String hash1 = cap.getAllValues().get(0).getContentHash();
            String hash2 = cap.getAllValues().get(1).getContentHash();
            Assertions.assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("createdAt установлен")
        void createdAtIsSet() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLog("{\"x\":1}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getCreatedAt()).isNotNull();
        }
    }

    // ── handleTransferEvent ───────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTransferEvent")
    class HandleTransferEvent {

        @Test
        @DisplayName("serviceName=transfer-service, eventType=TRANSFER")
        void setsCorrectFields() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleTransferEvent("{\"transferId\":42}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("transfer-service");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("TRANSFER");
        }

        @Test
        @DisplayName("дубль → пропускается")
        void duplicate_skips() {
            when(historyService.existsByContentHash(anyString())).thenReturn(true);

            listener.handleTransferEvent("{\"transferId\":42}");

            verify(historyService, never()).saveHistory(any());
        }
    }

    // ── handleAccountEvent ────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAccountEvent")
    class HandleAccountEvent {

        @Test
        @DisplayName("serviceName=account-service, eventType=ACCOUNT")
        void setsCorrectFields() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAccountEvent("{\"accountId\":7}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("account-service");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("ACCOUNT");
        }

        @Test
        @DisplayName("новое событие → saved++")
        void newEvent_incrementsSaved() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAccountEvent("{\"accountId\":99}");

            verify(savedCounter).increment();
        }
    }

    // ── handleErrorLog ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleErrorLog")
    class HandleErrorLog {

        @Test
        @DisplayName("serviceName=error, eventType=ERROR")
        void setsCorrectFields() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleErrorLog("{\"error\":\"NullPointerException\"}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("error");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("ERROR");
        }
    }

    // ── handleAuditLogDlq ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleAuditLogDlq")
    class HandleAuditLogDlq {

        @Test
        @DisplayName("DLQ событие → dlqEventsReceived++ и saveHistory вызывается")
        void dlqEvent_saveAndIncrementsDlq() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLogDlq("{\"dlq\":true}");

            verify(dlqCounter).increment();
            verify(historyService).saveHistory(any(History.class));
        }

        @Test
        @DisplayName("serviceName=DLQ, eventType=DLQ_AUDIT")
        void setsCorrectFields() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleAuditLogDlq("{\"dlq\":\"audit\"}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("DLQ");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("DLQ_AUDIT");
        }

        @Test
        @DisplayName("дубль DLQ → saveHistory НЕ вызывается, dlq++ всё равно")
        void duplicateDlq_skipsSave() {
            when(historyService.existsByContentHash(anyString())).thenReturn(true);

            listener.handleAuditLogDlq("{\"dlq\":true}");

            verify(dlqCounter).increment();
            verify(historyService, never()).saveHistory(any());
        }
    }

    // ── handleErrorLogDlq ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleErrorLogDlq")
    class HandleErrorLogDlq {

        @Test
        @DisplayName("DLQ error событие → serviceName=DLQ, eventType=DLQ_ERROR")
        void setsCorrectFields() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);

            listener.handleErrorLogDlq("{\"err\":\"timeout\"}");

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService).saveHistory(cap.capture());
            Assertions.assertThat(cap.getValue().getServiceName()).isEqualTo("DLQ");
            Assertions.assertThat(cap.getValue().getEventType()).isEqualTo("DLQ_ERROR");
        }

        @Test
        @DisplayName("DLQ error дубль → dlq++ но saveHistory НЕ вызывается")
        void duplicateDlqError_skipsSave() {
            when(historyService.existsByContentHash(anyString())).thenReturn(true);

            listener.handleErrorLogDlq("{\"err\":\"timeout\"}");

            verify(dlqCounter).increment();
            verify(historyService, never()).saveHistory(any());
        }
    }

    // ── Сценарий последовательной обработки ──────────────────────────────────

    @Nested
    @DisplayName("Последовательная обработка")
    class SequentialProcessing {

        @Test
        @DisplayName("одинаковый payload дважды: сохранение только первый раз")
        void samePayloadTwice_savedOnlyOnce() {
            String payload = "{\"event\":\"audit\",\"id\":100}";
            when(historyService.existsByContentHash(anyString()))
                    .thenReturn(false)
                    .thenReturn(true);

            listener.handleAuditLog(payload);
            listener.handleAuditLog(payload);

            verify(historyService, times(1)).saveHistory(any());
            verify(savedCounter, times(1)).increment();
            verify(skippedCounter, times(1)).increment();
        }

        @Test
        @DisplayName("разные топики с одинаковым payload → разные хэши (serviceName в хэше)")
        void differentTopics_samePayload_differentHashes() {
            when(historyService.existsByContentHash(anyString())).thenReturn(false);
            String payload = "{\"id\":1}";

            listener.handleAuditLog(payload);
            listener.handleTransferEvent(payload);

            ArgumentCaptor<History> cap = ArgumentCaptor.forClass(History.class);
            verify(historyService, times(2)).saveHistory(cap.capture());
            String hashAudit    = cap.getAllValues().get(0).getContentHash();
            String hashTransfer = cap.getAllValues().get(1).getContentHash();
            // serviceName входит в хэш → разные сервисы дают разные хэши
            Assertions.assertThat(hashAudit).isNotEqualTo(hashTransfer);
        }
    }
}
