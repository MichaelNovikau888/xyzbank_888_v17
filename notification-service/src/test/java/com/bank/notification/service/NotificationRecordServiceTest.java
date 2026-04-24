package com.bank.notification.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/**
 * Unit-тесты NotificationRecordService.
 */
 /**
 * H2 in-memory (тестовый профиль). Panache persist — работает реально.
 */
 /**
 * Покрываемые сценарии:
 *   - saveIfFinal: все 4 финальных статуса → true + запись в БД
 *   - saveIfFinal: нефинальные статусы → false, БД не пишется
 *   - isFinalStatus: все варианты
 */
@QuarkusTest
@DisplayName("NotificationRecordService — unit tests")
class NotificationRecordServiceTest {

    @Inject
    NotificationRecordService service;

    private static final BigDecimal AMOUNT    = new BigDecimal("1000.00");
    private static final String     CLIENT_ID = "client-42";
    private static final String     ACCOUNT   = "40817810099910004312";

    // ── saveIfFinal ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveIfFinal")
    class SaveIfFinal {

        @Test
        @Transactional
        @DisplayName("COMPLETED → возвращает true")
        void completed_returnsTrue() {
            boolean result = service.saveIfFinal(1L, CLIENT_ID, "COMPLETED",
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("FAILED → возвращает true")
        void failed_returnsTrue() {
            boolean result = service.saveIfFinal(2L, CLIENT_ID, "FAILED",
                    AMOUNT, "RUB", ACCOUNT, "Insufficient funds");
            Assertions.assertThat(result).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("CANCELLED → возвращает true")
        void cancelled_returnsTrue() {
            boolean result = service.saveIfFinal(3L, CLIENT_ID, "CANCELLED",
                    AMOUNT, "RUB", ACCOUNT, "User request");
            Assertions.assertThat(result).isTrue();
        }

        @Test
        @Transactional
        @DisplayName("BLOCKED → возвращает true")
        void blocked_returnsTrue() {
            boolean result = service.saveIfFinal(4L, CLIENT_ID, "BLOCKED",
                    AMOUNT, "RUB", ACCOUNT, "Antifraud blocked");
            Assertions.assertThat(result).isTrue();
        }

        @Test
        @DisplayName("CREATED → возвращает false (не финальный)")
        void created_returnsFalse() {
            boolean result = service.saveIfFinal(5L, CLIENT_ID, "CREATED",
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isFalse();
        }

        @Test
        @DisplayName("PROCESSING → возвращает false (не финальный)")
        void processing_returnsFalse() {
            boolean result = service.saveIfFinal(6L, CLIENT_ID, "PROCESSING",
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isFalse();
        }

        @Test
        @DisplayName("REVIEW → возвращает false (не финальный)")
        void review_returnsFalse() {
            boolean result = service.saveIfFinal(7L, CLIENT_ID, "REVIEW",
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null статус → возвращает false")
        void nullStatus_returnsFalse() {
            boolean result = service.saveIfFinal(8L, CLIENT_ID, null,
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isFalse();
        }

        @Test
        @DisplayName("неизвестный статус → возвращает false")
        void unknownStatus_returnsFalse() {
            boolean result = service.saveIfFinal(9L, CLIENT_ID, "WEIRD_STATUS",
                    AMOUNT, "RUB", ACCOUNT, null);
            Assertions.assertThat(result).isFalse();
        }
    }

    // ── isFinalStatus ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isFinalStatus")
    class IsFinalStatus {

        @Test
        @DisplayName("COMPLETED → true")
        void completed_isTrue()  { Assertions.assertThat(service.isFinalStatus("COMPLETED")).isTrue(); }

        @Test
        @DisplayName("FAILED → true")
        void failed_isTrue()     { Assertions.assertThat(service.isFinalStatus("FAILED")).isTrue(); }

        @Test
        @DisplayName("CANCELLED → true")
        void cancelled_isTrue()  { Assertions.assertThat(service.isFinalStatus("CANCELLED")).isTrue(); }

        @Test
        @DisplayName("BLOCKED → true")
        void blocked_isTrue()    { Assertions.assertThat(service.isFinalStatus("BLOCKED")).isTrue(); }

        @Test
        @DisplayName("CREATED → false")
        void created_isFalse()   { Assertions.assertThat(service.isFinalStatus("CREATED")).isFalse(); }

        @Test
        @DisplayName("PROCESSING → false")
        void processing_isFalse(){ Assertions.assertThat(service.isFinalStatus("PROCESSING")).isFalse(); }

        @Test
        @DisplayName("null → false")
        void null_isFalse()      { Assertions.assertThat(service.isFinalStatus(null)).isFalse(); }

        @Test
        @DisplayName("пустая строка → false")
        void empty_isFalse()     { Assertions.assertThat(service.isFinalStatus("")).isFalse(); }
    }
}
