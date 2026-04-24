package com.bank.antifraud.integration;

import com.bank.antifraud.dto.SuspiciousAccountTransferDto;
import com.bank.antifraud.dto.SuspiciousCardTransferDto;
import com.bank.antifraud.dto.SuspiciousPhoneTransferDto;
import com.bank.antifraud.repository.SuspiciousAccountTransferRepository;
import com.bank.antifraud.repository.SuspiciousCardTransferRepository;
import com.bank.antifraud.repository.SuspiciousPhoneTransferRepository;
import com.bank.antifraud.service.SuspiciousTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты анализа подозрительных переводов.
 */
 /**
 * Что нельзя проверить Mockito-тестами, но критично для финансового сервиса:
 */
 /**
 * 1. Уникальный индекс suspicious_*_id_key — реальный PostgreSQL проверит нарушение.
 * 2. Liquibase-миграции применяются корректно — все три таблицы созданы.
 * 3. Spring Data JDBC vs JPA — JDBC-репозитории работают с реальной схемой.
 * 4. Граничные значения порогов — бизнес-логика на реальных данных.
 */
 /**
 * Примечание: Transactional Outbox (suspicious-transfers.result) удалён как мёртвый топик
 * в релизе 0.3.0.0 — антифрод-ответ теперь отправляется напрямую через
 * SuspiciousTransferProducer при обработке payment.antifraud.check / transfer.antifraud.check.
 */
@DisplayName("Antifraud Service — Integration Tests")
class AntifraudAnalysisIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SuspiciousTransferService        transferService;
    @Autowired private SuspiciousCardTransferRepository    cardRepo;
    @Autowired private SuspiciousPhoneTransferRepository   phoneRepo;
    @Autowired private SuspiciousAccountTransferRepository accountRepo;

    @BeforeEach
    void setUp() {
        cardRepo.deleteAll();
        phoneRepo.deleteAll();
        accountRepo.deleteAll();
    }

    // ── Тест 1: Большая сумма — карта ─────────────────────────────────────────

    @Test
    @DisplayName("Карта > 50 000: analyzeCardTransfer → blocked=true, suspicious=true, запись в БД")
    void analyzeCardTransfer_largeAmount_blockedAndSaved() {
        Integer transferId = 101;

        SuspiciousCardTransferDto result =
                transferService.analyzeCardTransfer(new BigDecimal("55000.00"), transferId);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.getCardTransferId()).isEqualTo(transferId);

        assertThat(cardRepo.count()).isEqualTo(1);
        var saved = cardRepo.findById(result.getId()).orElseThrow();
        assertThat(saved.isBlocked()).isTrue();
    }

    // ── Тест 2: Нормальная сумма — карта ──────────────────────────────────────

    @Test
    @DisplayName("Карта <= 10 000: analyzeCardTransfer → blocked=false, запись всё равно сохраняется")
    void analyzeCardTransfer_normalAmount_notBlocked() {
        SuspiciousCardTransferDto result =
                transferService.analyzeCardTransfer(new BigDecimal("5000.00"), 102);

        assertThat(result.isBlocked()).isFalse();
        assertThat(result.isSuspicious()).isFalse();
        assertThat(cardRepo.count()).isEqualTo(1);
    }

    // ── Тест 3: Пограничный REVIEW — карта ───────────────────────────────────

    @Test
    @DisplayName("Карта 10 001–50 000: analyzeCardTransfer → suspicious=true, blocked=false (REVIEW)")
    void analyzeCardTransfer_reviewRange_suspiciousNotBlocked() {
        SuspiciousCardTransferDto result =
                transferService.analyzeCardTransfer(new BigDecimal("25000.00"), 103);

        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.isBlocked()).isFalse();
        assertThat(cardRepo.count()).isEqualTo(1);
    }

    // ── Тест 4: Телефонный перевод ────────────────────────────────────────────

    @Test
    @DisplayName("Телефон > 50 000: analyzePhoneTransfer → blocked=true, запись в phone-таблице")
    void analyzePhoneTransfer_largeAmount_blockedAndSaved() {
        Integer transferId = 201;

        SuspiciousPhoneTransferDto result =
                transferService.analyzePhoneTransfer(new BigDecimal("60000.00"), transferId);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getPhoneTransferId()).isEqualTo(transferId);
        assertThat(phoneRepo.count()).isEqualTo(1);
    }

    // ── Тест 5: Счётный перевод ───────────────────────────────────────────────

    @Test
    @DisplayName("Счёт > 50 000: analyzeAccountTransfer → blocked=true, отдельная таблица")
    void analyzeAccountTransfer_largeAmount_blockedInSeparateTable() {
        Integer transferId = 301;

        SuspiciousAccountTransferDto result =
                transferService.analyzeAccountTransfer(new BigDecimal("75000.00"), transferId);

        assertThat(result.isBlocked()).isTrue();
        assertThat(result.getAccountTransferId()).isEqualTo(transferId);

        assertThat(accountRepo.count()).isEqualTo(1);
        assertThat(cardRepo.count()).isZero();
        assertThat(phoneRepo.count()).isZero();
    }

    // ── Тест 6: Уникальный индекс на transfer_id ──────────────────────────────

    @Test
    @DisplayName("Уникальный индекс: дублирование transfer_id → исключение (PostgreSQL constraint)")
    void analyzeCardTransfer_duplicateTransferId_throwsConstraintViolation() {
        Integer transferId = 999;

        transferService.analyzeCardTransfer(new BigDecimal("5000.00"), transferId);
        assertThat(cardRepo.count()).isEqualTo(1);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> transferService.analyzeCardTransfer(new BigDecimal("8000.00"), transferId));
    }

    // ── Тест 7: Чтение сохранённого анализа ──────────────────────────────────

    @Test
    @DisplayName("getCardTransfer возвращает сохранённый результат анализа из PostgreSQL")
    void getCardTransfer_afterAnalysis_returnsPersistedResult() {
        Integer transferId = 501;
        transferService.analyzeCardTransfer(new BigDecimal("3000.00"), transferId);

        var saved = cardRepo.findAll().iterator().next();
        SuspiciousCardTransferDto fetched =
                transferService.getCardTransfer(Math.toIntExact(saved.getId()));

        assertThat(fetched.getCardTransferId()).isEqualTo(transferId);
        assertThat(fetched.isBlocked()).isFalse();
        assertThat(fetched.isSuspicious()).isFalse();
    }

    // ── Тест 8: Три типа — изолированные таблицы ─────────────────────────────

    @Test
    @DisplayName("Три типа анализа → три разные таблицы")
    void analyzeAllThreeTypes_writeToSeparateTables() {
        transferService.analyzeCardTransfer(new BigDecimal("1000.00"), 601);
        transferService.analyzePhoneTransfer(new BigDecimal("2000.00"), 602);
        transferService.analyzeAccountTransfer(new BigDecimal("3000.00"), 603);

        assertThat(cardRepo.count()).isEqualTo(1);
        assertThat(phoneRepo.count()).isEqualTo(1);
        assertThat(accountRepo.count()).isEqualTo(1);
    }

    // ── Тест 9: Граничное значение порога ─────────────────────────────────────

    @Test
    @DisplayName("Граничное значение: сумма ровно 10 000 — ALLOW (порог строго >)")
    void analyzeCardTransfer_exactAllowThreshold_notBlocked() {
        SuspiciousCardTransferDto result =
                transferService.analyzeCardTransfer(new BigDecimal("10000.00"), 701);

        assertThat(result.isBlocked()).isFalse();
        assertThat(result.isSuspicious()).isFalse();
    }

    // ── Тест 10: Граничное значение блокировки ────────────────────────────────

    @Test
    @DisplayName("Граничное значение: сумма ровно 50 000 — REVIEW (не блокируется)")
    void analyzeCardTransfer_exactBlockThreshold_reviewNotBlocked() {
        SuspiciousCardTransferDto result =
                transferService.analyzeCardTransfer(new BigDecimal("50000.00"), 702);

        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.isBlocked()).isFalse();
    }

    // ── Тест 11: Liquibase создала все таблицы ────────────────────────────────

    @Test
    @DisplayName("Все Liquibase-таблицы созданы и доступны через Spring Data JDBC")
    void liquibase_allRequiredTablesExist() {
        assertThat(cardRepo.count()).isGreaterThanOrEqualTo(0);
        assertThat(phoneRepo.count()).isGreaterThanOrEqualTo(0);
        assertThat(accountRepo.count()).isGreaterThanOrEqualTo(0);
        // outbox_events таблица сохранена в БД для обратной совместимости,
        // но OutboxHelper больше не пишет в неё
    }
}
