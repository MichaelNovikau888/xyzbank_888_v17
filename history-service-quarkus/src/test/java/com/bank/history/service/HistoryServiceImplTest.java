package com.bank.history.service;

import com.bank.history.dto.HistoryDto;
import com.bank.history.dto.PagedResponse;
import com.bank.history.entity.History;
import com.bank.history.mapper.HistoryMapper;
import com.bank.history.repository.HistoryRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты HistoryServiceImpl.
 * Репозиторий и маппер — моки; CDI-контекст минимальный.
 */
@QuarkusTest
@DisplayName("HistoryServiceImpl — unit tests")
class HistoryServiceImplTest {

    @InjectMock HistoryRepository historyRepository;
    @InjectMock HistoryMapper     historyMapper;

    @Inject HistoryService historyService;

    // ── helpers ───────────────────────────────────────────────────────────────

    private History entity(Long id, String service, String type, String hash) {
        History h = new History();
        h.setId(id);
        h.setServiceName(service);
        h.setEventType(type);
        h.setEventData("{\"id\":" + id + "}");
        h.setContentHash(hash);
        h.setCreatedAt(LocalDateTime.now());
        return h;
    }

    private HistoryDto dto(Long id, String service, String type) {
        HistoryDto d = new HistoryDto();
        d.setId(id);
        d.setServiceName(service);
        d.setEventType(type);
        return d;
    }

    // ── saveHistory ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveHistory")
    class SaveHistory {

        @Test
        @DisplayName("вызывает persist на репозитории")
        void callsPersist() {
            History h = entity(1L, "transfer-service", "TRANSFER", "abc");
            doNothing().when(historyRepository).persist(any(History.class));

            historyService.saveHistory(h);

            verify(historyRepository).persist(h);
        }
    }

    // ── existsByContentHash ───────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByContentHash")
    class ExistsByContentHash {

        @Test
        @DisplayName("true — когда хэш найден")
        void trueWhenFound() {
            when(historyRepository.findByContentHash("abc"))
                    .thenReturn(Optional.of(entity(1L, "svc", "T", "abc")));

            Assertions.assertThat(historyService.existsByContentHash("abc")).isTrue();
        }

        @Test
        @DisplayName("false — когда хэш не найден")
        void falseWhenNotFound() {
            when(historyRepository.findByContentHash("xyz")).thenReturn(Optional.empty());

            Assertions.assertThat(historyService.existsByContentHash("xyz")).isFalse();
        }
    }

    // ── getAuditHistory ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAuditHistory")
    class GetAuditHistory {

        @Test
        @DisplayName("page < 0 → IllegalArgumentException")
        void negativePage_throws() {
            Assertions.assertThatThrownBy(() -> historyService.getAuditHistory(-1, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page index must not be less than zero");
        }

        @Test
        @DisplayName("size = 0 → IllegalArgumentException")
        void zeroSize_throws() {
            Assertions.assertThatThrownBy(() -> historyService.getAuditHistory(0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page size must not be less than one");
        }

        @Test
        @DisplayName("size > 200 → IllegalArgumentException")
        void oversizeSize_throws() {
            Assertions.assertThatThrownBy(() -> historyService.getAuditHistory(0, 201))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Page size must not exceed 200");
        }
    }

    // ── getAuditHistoryByServiceName ──────────────────────────────────────────

    @Nested
    @DisplayName("getAuditHistoryByServiceName")
    class GetByServiceName {

        @Test
        @DisplayName("возвращает PagedResponse когда данные есть")
        void returnsData() {
            History h = entity(2L, "account-service", "ACCOUNT", "h2");
            HistoryDto d = dto(2L, "account-service", "ACCOUNT");
            when(historyRepository.count("serviceName", "account-service")).thenReturn(1L);
            when(historyRepository.findByServiceName("account-service", 0, 20))
                    .thenReturn(List.of(h));
            when(historyMapper.toDto(h)).thenReturn(d);

            PagedResponse<HistoryDto> result =
                    historyService.getAuditHistoryByServiceName("account-service", 0, 20);

            Assertions.assertThat(result.getTotalElements()).isEqualTo(1L);
            Assertions.assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("serviceName пустой → IllegalArgumentException")
        void blankName_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByServiceName("  ", 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Service name must not be blank");
        }

        @Test
        @DisplayName("serviceName null → IllegalArgumentException")
        void nullName_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByServiceName(null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("нет записей → EntityNotFoundException")
        void noRecords_throwsNotFound() {
            when(historyRepository.count("serviceName", "ghost-service")).thenReturn(0L);

            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByServiceName("ghost-service", 0, 20))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("ghost-service");

            verify(historyRepository, never()).findByServiceName(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("page < 0 → IllegalArgumentException")
        void negativePage_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByServiceName("svc", -1, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getAuditHistoryByEventType ────────────────────────────────────────────

    @Nested
    @DisplayName("getAuditHistoryByEventType")
    class GetByEventType {

        @Test
        @DisplayName("возвращает PagedResponse когда данные есть")
        void returnsData() {
            History h = entity(3L, "transfer-service", "TRANSFER", "h3");
            HistoryDto d = dto(3L, "transfer-service", "TRANSFER");
            when(historyRepository.count("eventType", "TRANSFER")).thenReturn(1L);
            when(historyRepository.findByEventType("TRANSFER", 0, 20)).thenReturn(List.of(h));
            when(historyMapper.toDto(h)).thenReturn(d);

            PagedResponse<HistoryDto> result =
                    historyService.getAuditHistoryByEventType("TRANSFER", 0, 20);

            Assertions.assertThat(result.getTotalElements()).isEqualTo(1L);
        }

        @Test
        @DisplayName("eventType пустой → IllegalArgumentException")
        void blankType_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByEventType("", 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Event type must not be blank");
        }

        @Test
        @DisplayName("нет записей → EntityNotFoundException")
        void noRecords_throwsNotFound() {
            when(historyRepository.count("eventType", "UNKNOWN")).thenReturn(0L);

            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByEventType("UNKNOWN", 0, 20))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("size > 200 → IllegalArgumentException")
        void oversizeSize_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByEventType("TRANSFER", 0, 201))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getAuditHistoryByTransferId ───────────────────────────────────────────

    @Nested
    @DisplayName("getAuditHistoryByTransferId")
    class GetByTransferId {

        @Test
        @DisplayName("возвращает PagedResponse когда данные есть")
        void returnsData() {
            History h = entity(4L, "transfer-service", "TRANSFER", "h4");
            h.setTransferAuditId(42L);
            HistoryDto d = dto(4L, "transfer-service", "TRANSFER");
            when(historyRepository.countByTransferAuditId(42L)).thenReturn(1L);
            when(historyRepository.findByTransferAuditIdPaged(42L, 0, 20)).thenReturn(List.of(h));
            when(historyMapper.toDto(h)).thenReturn(d);

            PagedResponse<HistoryDto> result =
                    historyService.getAuditHistoryByTransferId(42L, 0, 20);

            Assertions.assertThat(result.getTotalElements()).isEqualTo(1L);
            Assertions.assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("null ID → IllegalArgumentException")
        void nullId_throws() {
            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByTransferId(null, 0, 20))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transferAuditId cannot be null");
        }

        @Test
        @DisplayName("нет записей → EntityNotFoundException")
        void noRecords_throwsNotFound() {
            when(historyRepository.countByTransferAuditId(999L)).thenReturn(0L);

            Assertions.assertThatThrownBy(
                    () -> historyService.getAuditHistoryByTransferId(999L, 0, 20))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ── getRecentEvents ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRecentEvents")
    class GetRecentEvents {

        @Test
        @DisplayName("возвращает список последних N событий")
        void returnsList() {
            History h1 = entity(10L, "audit.logs", "AUDIT", "h10");
            History h2 = entity(11L, "error",      "ERROR", "h11");
            HistoryDto d1 = dto(10L, "audit.logs", "AUDIT");
            HistoryDto d2 = dto(11L, "error",      "ERROR");
            when(historyRepository.findRecentEvents(2)).thenReturn(List.of(h1, h2));
            when(historyMapper.toDto(h1)).thenReturn(d1);
            when(historyMapper.toDto(h2)).thenReturn(d2);

            List<HistoryDto> result = historyService.getRecentEvents(2);

            Assertions.assertThat(result).hasSize(2);
            Assertions.assertThat(result.get(0).getEventType()).isEqualTo("AUDIT");
        }

        @Test
        @DisplayName("limit = 0 → IllegalArgumentException")
        void zeroLimit_throws() {
            Assertions.assertThatThrownBy(() -> historyService.getRecentEvents(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit must be between 1 and 100");
        }

        @Test
        @DisplayName("limit > 100 → IllegalArgumentException")
        void oversizeLimit_throws() {
            Assertions.assertThatThrownBy(() -> historyService.getRecentEvents(101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit must be between 1 and 100");
        }

        @Test
        @DisplayName("пустая БД → пустой список")
        void emptyDb_returnsEmpty() {
            when(historyRepository.findRecentEvents(5)).thenReturn(List.of());

            List<HistoryDto> result = historyService.getRecentEvents(5);

            Assertions.assertThat(result).isEmpty();
        }
    }
}
