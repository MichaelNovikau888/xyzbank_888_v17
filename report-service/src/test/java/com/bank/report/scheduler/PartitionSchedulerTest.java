package com.bank.report.scheduler;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты PartitionScheduler.
 *
 * <p>Планировщик отключён через {@code %test.quarkus.scheduler.enabled=false},
 * поэтому {@code @Scheduled}-метод не вызывается автоматически.
 * Тестируем {@code ensurePartitions()} и {@code createPartitionIfAbsent()} напрямую
 * через мок DataSource.
 */
@QuarkusTest
@DisplayName("PartitionScheduler — unit tests")
class PartitionSchedulerTest {

    @Inject
    PartitionScheduler scheduler;

    @InjectMock
    AgroalDataSource dataSource;

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Настраивает мок DataSource так, что pg_tables возвращает пустой ResultSet
     * (партиции не существуют) и CREATE выполняется успешно.
     */
    private void mockPartitionAbsent() throws Exception {
        Connection conn  = mock(Connection.class);
        PreparedStatement checkPs  = mock(PreparedStatement.class);
        PreparedStatement createPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        // первый prepareStatement — SELECT для проверки наличия
        // второй — CREATE TABLE
        when(conn.prepareStatement(contains("pg_tables"))).thenReturn(checkPs);
        when(conn.prepareStatement(contains("CREATE TABLE"))).thenReturn(createPs);
        when(checkPs.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);  // партиции нет
    }

    /**
     * Настраивает мок DataSource: pg_tables возвращает запись (партиция уже есть).
     */
    private void mockPartitionExists() throws Exception {
        Connection conn      = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs         = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(contains("pg_tables"))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);  // партиция существует
    }

    // ── тесты ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MONTHS_AHEAD = 3: создаётся 3 × 2 таблицы = 6 партиций")
    void ensurePartitions_creates6Partitions() throws Exception {
        mockPartitionAbsent();

        scheduler.ensurePartitions();

        // 3 месяца × 2 таблицы = 6 CREATE TABLE IF NOT EXISTS
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(dataSource.getConnection().prepareStatement(anyString()), atLeast(6));
    }

    @Test
    @DisplayName("createPartitionsMonthly не бросает исключений")
    void createPartitionsMonthly_noException() throws Exception {
        mockPartitionAbsent();
        assertThatCode(() -> scheduler.createPartitionsMonthly()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("если партиция уже существует — CREATE не вызывается")
    void partitionExists_skipCreate() throws Exception {
        mockPartitionExists();

        Connection conn = dataSource.getConnection();
        PreparedStatement createPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(contains("CREATE TABLE"))).thenReturn(createPs);

        scheduler.ensurePartitions();

        // CREATE не должен вызываться ни разу (только CHECK)
        verify(createPs, never()).execute();
    }

    @Test
    @DisplayName("MONTHS_AHEAD = 3: партиции начинаются с текущего месяца")
    void ensurePartitions_startsFromCurrentMonth() throws Exception {
        mockPartitionAbsent();

        // Захватываем SQL CREATE TABLE
        Connection conn  = mock(Connection.class);
        PreparedStatement checkPs  = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(contains("pg_tables"))).thenReturn(checkPs);
        when(checkPs.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        ArgumentCaptor<String> createSqlCaptor = ArgumentCaptor.forClass(String.class);
        PreparedStatement createPs = mock(PreparedStatement.class);
        when(conn.prepareStatement(createSqlCaptor.capture())).thenReturn(createPs);

        scheduler.ensurePartitions();

        // Хотя бы один CREATE должен содержать текущий год
        String currentYear = String.valueOf(LocalDate.now().getYear());
        boolean hasCurrentYear = createSqlCaptor.getAllValues().stream()
                .filter(s -> s.contains("CREATE TABLE"))
                .anyMatch(s -> s.contains(currentYear));
        assertThat(hasCurrentYear).isTrue();
    }

    @Test
    @DisplayName("SQLException при CREATE не пробрасывается (warn-only)")
    void createPartition_sqlException_noThrow() throws Exception {
        Connection conn      = mock(Connection.class);
        PreparedStatement checkPs  = mock(PreparedStatement.class);
        PreparedStatement createPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(contains("pg_tables"))).thenReturn(checkPs);
        when(conn.prepareStatement(contains("CREATE TABLE"))).thenReturn(createPs);
        when(checkPs.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
        when(createPs.execute()).thenThrow(new java.sql.SQLException("concurrent create"));

        // Должен логировать warn, но не пробрасывать
        assertThatCode(() -> scheduler.ensurePartitions()).doesNotThrowAnyException();
    }
}
