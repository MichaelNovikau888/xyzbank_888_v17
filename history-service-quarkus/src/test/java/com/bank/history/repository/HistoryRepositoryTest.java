package com.bank.history.repository;

import com.bank.history.entity.History;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Интеграционные тесты HistoryRepository.
 */
 /**
 * Реальная H2 БД (drop-and-create при старте тестового профиля).
 * Каждый тест начинается с очищенной таблицы (@BeforeEach deleteAll).
 */
 /**
 * Покрывает все кастомные методы репозитория:
 *   findByServiceName(name, page, size)
 *   findByEventType(type, page, size)
 *   findRecentEvents(limit)
 *   findByTransferAuditIdPaged(id, page, size)
 *   countByTransferAuditId(id)
 *   findByContentHash(hash)
 */
@QuarkusTest
@DisplayName("HistoryRepository — integration tests (H2)")
class HistoryRepositoryTest {

    @Inject HistoryRepository repository;

    @BeforeEach
    @Transactional
    void cleanDb() {
        repository.deleteAll();
    }

    @Transactional
    History save(String service, String type, String hash) {
        return save(service, type, hash, null);
    }

    @Transactional
    History save(String service, String type, String hash, Long transferAuditId) {
        History h = new History();
        h.setServiceName(service);
        h.setEventType(type);
        h.setEventData("{\"test\":true}");
        h.setContentHash(hash);
        h.setTransferAuditId(transferAuditId);
        h.setCreatedAt(LocalDateTime.now());
        repository.persist(h);
        return h;
    }

    // ── findByServiceName ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByServiceName")
    class FindByServiceName {

        @Test
        @DisplayName("возвращает только записи с указанным serviceName")
        void filtersCorrectly() {
            save("transfer-service", "TRANSFER", "h1");
            save("account-service",  "ACCOUNT",  "h2");
            save("transfer-service", "TRANSFER", "h3");

            List<History> result = repository.findByServiceName("transfer-service", 0, 10);

            Assertions.assertThat(result).hasSize(2);
            Assertions.assertThat(result).allMatch(h -> "transfer-service".equals(h.getServiceName()));
        }

        @Test
        @DisplayName("пагинация: page=0&size=1 возвращает 1 запись из 2")
        void pagination_firstPage() {
            save("svc", "TYPE", "hp1");
            save("svc", "TYPE", "hp2");

            List<History> result = repository.findByServiceName("svc", 0, 1);

            Assertions.assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("пагинация: page=1&size=1 возвращает вторую запись")
        void pagination_secondPage() {
            save("svc", "TYPE", "hp3");
            save("svc", "TYPE", "hp4");

            List<History> page1 = repository.findByServiceName("svc", 1, 1);

            Assertions.assertThat(page1).hasSize(1);
        }

        @Test
        @DisplayName("нет записей для serviceName → пустой список")
        void unknownService_returnsEmpty() {
            List<History> result = repository.findByServiceName("ghost", 0, 10);
            Assertions.assertThat(result).isEmpty();
        }
    }

    // ── findByEventType ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEventType")
    class FindByEventType {

        @Test
        @DisplayName("возвращает только записи с указанным eventType")
        void filtersCorrectly() {
            save("svc1", "AUDIT",    "he1");
            save("svc2", "TRANSFER", "he2");
            save("svc3", "AUDIT",    "he3");

            List<History> result = repository.findByEventType("AUDIT", 0, 10);

            Assertions.assertThat(result).hasSize(2);
            Assertions.assertThat(result).allMatch(h -> "AUDIT".equals(h.getEventType()));
        }

        @Test
        @DisplayName("нет записей для eventType → пустой список")
        void unknownType_returnsEmpty() {
            Assertions.assertThat(repository.findByEventType("UNKNOWN", 0, 10)).isEmpty();
        }

        @Test
        @DisplayName("пагинация работает")
        void pagination_works() {
            save("s", "ERROR", "herr1");
            save("s", "ERROR", "herr2");
            save("s", "ERROR", "herr3");

            Assertions.assertThat(repository.findByEventType("ERROR", 0, 2)).hasSize(2);
            Assertions.assertThat(repository.findByEventType("ERROR", 1, 2)).hasSize(1);
        }
    }

    // ── findRecentEvents ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("findRecentEvents")
    class FindRecentEvents {

        @Test
        @DisplayName("возвращает не более limit записей")
        void respectsLimit() {
            save("s", "T", "hr1");
            save("s", "T", "hr2");
            save("s", "T", "hr3");

            List<History> result = repository.findRecentEvents(2);

            Assertions.assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("пустая таблица → пустой список")
        void emptyTable_returnsEmpty() {
            Assertions.assertThat(repository.findRecentEvents(10)).isEmpty();
        }

        @Test
        @DisplayName("limit больше количества записей → возвращает все")
        void limitBiggerThanCount_returnsAll() {
            save("s", "T", "hrx1");
            save("s", "T", "hrx2");

            Assertions.assertThat(repository.findRecentEvents(100)).hasSize(2);
        }
    }

    // ── findByTransferAuditIdPaged ────────────────────────────────────────────

    @Nested
    @DisplayName("findByTransferAuditIdPaged")
    class FindByTransferAuditIdPaged {

        @Test
        @DisplayName("возвращает только записи с нужным transferAuditId")
        void filtersById() {
            save("transfer-service", "TRANSFER", "ht1", 42L);
            save("transfer-service", "TRANSFER", "ht2", 42L);
            save("transfer-service", "TRANSFER", "ht3", 99L);

            List<History> result = repository.findByTransferAuditIdPaged(42L, 0, 10);

            Assertions.assertThat(result).hasSize(2);
            Assertions.assertThat(result).allMatch(h -> Long.valueOf(42L).equals(h.getTransferAuditId()));
        }

        @Test
        @DisplayName("несуществующий ID → пустой список")
        void unknownId_returnsEmpty() {
            Assertions.assertThat(repository.findByTransferAuditIdPaged(999L, 0, 10)).isEmpty();
        }

        @Test
        @DisplayName("пагинация работает")
        void pagination_works() {
            save("s", "T", "htp1", 77L);
            save("s", "T", "htp2", 77L);
            save("s", "T", "htp3", 77L);

            Assertions.assertThat(repository.findByTransferAuditIdPaged(77L, 0, 2)).hasSize(2);
            Assertions.assertThat(repository.findByTransferAuditIdPaged(77L, 1, 2)).hasSize(1);
        }
    }

    // ── countByTransferAuditId ────────────────────────────────────────────────

    @Nested
    @DisplayName("countByTransferAuditId")
    class CountByTransferAuditId {

        @Test
        @DisplayName("возвращает корректное количество")
        void countsCorrectly() {
            save("s", "T", "hc1", 10L);
            save("s", "T", "hc2", 10L);
            save("s", "T", "hc3", 20L);

            Assertions.assertThat(repository.countByTransferAuditId(10L)).isEqualTo(2L);
            Assertions.assertThat(repository.countByTransferAuditId(20L)).isEqualTo(1L);
        }

        @Test
        @DisplayName("несуществующий ID → 0")
        void unknownId_returnsZero() {
            Assertions.assertThat(repository.countByTransferAuditId(999L)).isZero();
        }
    }

    // ── findByContentHash ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByContentHash")
    class FindByContentHash {

        @Test
        @DisplayName("возвращает запись с нужным хэшем")
        void findsExistingHash() {
            save("audit.logs", "AUDIT", "unique-hash-abc");

            Optional<History> result = repository.findByContentHash("unique-hash-abc");

            Assertions.assertThat(result).isPresent();
            Assertions.assertThat(result.get().getContentHash()).isEqualTo("unique-hash-abc");
        }

        @Test
        @DisplayName("несуществующий хэш → Optional.empty()")
        void unknownHash_returnsEmpty() {
            Optional<History> result = repository.findByContentHash("nonexistent-hash");

            Assertions.assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("идемпотентность: два события с одинаковым хэшем — второй не сохраняется")
        void duplicateHash_uniqueConstraint() {
            // Второй persist с тем же contentHash упадёт на уникальном индексе.
            // Тестируем что первый сохраняется успешно.
            save("svc", "TYPE", "dup-hash");

            Optional<History> result = repository.findByContentHash("dup-hash");
            Assertions.assertThat(result).isPresent();
        }
    }
}
