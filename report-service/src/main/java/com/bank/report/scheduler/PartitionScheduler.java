package com.bank.report.scheduler;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Планировщик автоматического создания месячных партиций PostgreSQL.
 *
 * <p>Проблема: Liquibase-миграция создаёт партиции на фиксированные месяцы
 * (2026-04, 05, 06). Если сервис работает дольше 3 месяцев — новые данные
 * падают в DEFAULT-партицию, которая со временем деградирует в производительности.
 *
 * <p>Решение: этот бин создаёт партиции на {@value #MONTHS_AHEAD} месяцев вперёд
 * от текущего месяца при каждом старте ({@link #onStart}) и ежемесячно по крону
 * ({@link #createPartitionsMonthly}).
 *
 * <p>Идемпотентность: используется {@code CREATE TABLE IF NOT EXISTS} —
 * повторные вызовы безопасны.
 *
 * <p>Тест-профиль: H2 не поддерживает партиционирование,
 * поэтому планировщик пропускает создание при {@code quarkus.scheduler.enabled=false}
 * (устанавливается в {@code %test} профиле).
 */
@ApplicationScoped
public class PartitionScheduler {

    private static final Logger LOG = Logger.getLogger(PartitionScheduler.class);

    /** Количество месяцев вперёд, для которых создаются партиции. */
    static final int MONTHS_AHEAD = 3;

    private static final DateTimeFormatter SUFFIX_FMT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final DateTimeFormatter DATE_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Имена партиционированных родительских таблиц. */
    private static final String[] TABLES = {"payment_reports", "transfer_reports"};

    @Inject
    AgroalDataSource dataSource;

    /**
     * Выполняется при старте приложения (до принятия первого HTTP-запроса).
     * Создаёт партиции на ближайшие {@value #MONTHS_AHEAD} месяца.
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info("PartitionScheduler: startup partition check...");
        ensurePartitions();
    }

    /**
     * Ежемесячное задание — 1-го числа каждого месяца в 00:05.
     * Создаёт партиции вперёд, чтобы они всегда были готовы заранее.
     */
    @Scheduled(cron = "0 5 0 1 * ?", identity = "partition-monthly")
    void createPartitionsMonthly() {
        LOG.info("PartitionScheduler: monthly partition job triggered");
        ensurePartitions();
    }

    /**
     * Создаёт партиции начиная с текущего месяца на {@value #MONTHS_AHEAD} месяцев вперёд.
     */
    void ensurePartitions() {
        LocalDate cursor = LocalDate.now().withDayOfMonth(1);
        for (int i = 0; i < MONTHS_AHEAD; i++) {
            LocalDate monthStart = cursor.plusMonths(i);
            LocalDate monthEnd   = monthStart.plusMonths(1);
            for (String table : TABLES) {
                createPartitionIfAbsent(table, monthStart, monthEnd);
            }
        }
        LOG.infof("PartitionScheduler: ensured partitions for %d months from %s",
                  MONTHS_AHEAD, cursor);
    }

    /**
     * Создаёт одну месячную партицию, если она ещё не существует.
     *
     * @param parentTable имя родительской таблицы (без схемы)
     * @param from        первый день месяца (включительно)
     * @param to          первый день следующего месяца (исключительно)
     */
    private void createPartitionIfAbsent(String parentTable, LocalDate from, LocalDate to) {
        String suffix    = from.format(SUFFIX_FMT);          // "2026_07"
        String partition = "report." + parentTable + "_" + suffix;
        String parent    = "report." + parentTable;
        String fromStr   = from.format(DATE_FMT);
        String toStr     = to.format(DATE_FMT);

        if (partitionExists(parentTable, parentTable + "_" + suffix)) {
            LOG.debugf("Partition %s already exists, skipping", partition);
            return;
        }

        String sql = String.format(
            "CREATE TABLE IF NOT EXISTS %s PARTITION OF %s " +
            "FOR VALUES FROM ('%s') TO ('%s')",
            partition, parent, fromStr, toStr);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            LOG.infof("PartitionScheduler: created partition %s [%s, %s)", partition, fromStr, toStr);
        } catch (SQLException e) {
            // Partition may have been created concurrently (race between replicas) — not fatal
            LOG.warnf("PartitionScheduler: could not create partition %s: %s", partition, e.getMessage());
        }
    }

    /**
     * Проверяет наличие партиции через pg_tables.
     */
    private boolean partitionExists(String schemaTable, String partitionName) {
        String sql = "SELECT 1 FROM pg_tables WHERE schemaname = 'report' AND tablename = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, partitionName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.warnf("PartitionScheduler: partitionExists check failed for %s: %s",
                      partitionName, e.getMessage());
            return false;
        }
    }
}
