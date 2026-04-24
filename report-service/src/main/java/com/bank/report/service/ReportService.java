package com.bank.report.service;

import com.bank.report.dto.PaymentReportDto;
import com.bank.report.dto.PeriodSummaryDto;
import com.bank.report.dto.TransferReportDto;
import com.bank.report.entity.PaymentReport;
import com.bank.report.entity.TransferReport;
import com.bank.report.metrics.ReportMetrics;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Бизнес-логика формирования отчётности.
 */
 /**
 * Kafka-обработчики вынесены в consumer/ (PaymentReportConsumer, TransferReportConsumer).
 * ReportService содержит только чистую бизнес-логику:
 *   - Отчёты за период (день / неделя / месяц)
 *   - Клиентский вид (только свои данные)
 *   - Бухгалтерский вид (все данные)
 *   - Генерация CSV
 */
@ApplicationScoped
public class ReportService {

    private static final Logger LOG = Logger.getLogger(ReportService.class);
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject AgroalDataSource dataSource;

    @Inject
    @DataSource("replica")
    AgroalDataSource replicaDataSource;

    @Inject ReportMetrics metrics;

    // ── Клиентский вид: только свои данные ───────────────────────────────────

 /**
     * Платежи клиента за конкретный день.
     * clientId = Profile.id клиента.
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PaymentReportDto> getClientPaymentsByDay(Long clientId, LocalDate date) {
        validateClientId(clientId);
        return PaymentReport.findByClientAndDate(clientId, date)
                .stream().map(this::toPaymentDto).toList();
    }

 /**
     * Переводы клиента за конкретный день.
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<TransferReportDto> getClientTransfersByDay(Long clientId, LocalDate date) {
        validateClientId(clientId);
        return TransferReport.findByClientAndDate(clientId, date)
                .stream().map(this::toTransferDto).toList();
    }

 /**
     * Сводка клиента за период (день / неделя / месяц).
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public PeriodSummaryDto getClientSummary(Long clientId, LocalDate from, LocalDate to,
                                              String periodLabel) {
        validateClientId(clientId);
        List<PaymentReport>  payments  = PaymentReport.findByClientAndDateRange(clientId, from, to);
        List<TransferReport> transfers = TransferReport.findByClientAndDateRange(clientId, from, to);
        return buildSummary(payments, transfers, periodLabel);
    }

    // ── Бухгалтерский вид: все данные банка ──────────────────────────────────

 /**
     * Все платежи за день (бухгалтерский отчёт).
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PaymentReportDto> getAllPaymentsByDay(LocalDate date) {
        return PaymentReport.findByDate(date).stream().map(this::toPaymentDto).toList();
    }

 /**
     * Все переводы за день (бухгалтерский отчёт).
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<TransferReportDto> getAllTransfersByDay(LocalDate date) {
        return TransferReport.findByDate(date).stream().map(this::toTransferDto).toList();
    }

 /**
     * Сводка по всем клиентам за период (бухгалтерия).
 */
    @Transactional(Transactional.TxType.SUPPORTS)
    public PeriodSummaryDto getBankSummary(LocalDate from, LocalDate to, String periodLabel) {
        List<PaymentReport>  payments  = PaymentReport.findByDateRange(from, to);
        List<TransferReport> transfers = TransferReport.findByDateRange(from, to);
        return buildSummary(payments, transfers, periodLabel);
    }

    // ── Вспомогательные: диапазоны дат ───────────────────────────────────────

    public static LocalDate[] weekRange(LocalDate date) {
        LocalDate start = date.with(DayOfWeek.MONDAY);
        LocalDate end   = date.with(DayOfWeek.SUNDAY);
        return new LocalDate[]{start, end};
    }

    public static LocalDate[] monthRange(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end   = date.withDayOfMonth(date.lengthOfMonth());
        return new LocalDate[]{start, end};
    }

    // ── CSV-генерация (бухгалтерия) ───────────────────────────────────────────

    @Transactional(Transactional.TxType.SUPPORTS)
    public byte[] generateDailyReportCsv(LocalDate date) {
        return metrics.getCsvGenerationTimer().record(() -> {
            try {
                byte[] result = doGenerateCsv(date);
                metrics.getCsvReportsGenerated().increment();
                return result;
            } catch (Exception e) {
                metrics.getCsvReportsFailed().increment();
                LOG.errorf(e, "CSV generation failed for date=%s", date);
                throw new RuntimeException("CSV generation failed", e);
            }
        });
    }

    private byte[] doGenerateCsv(LocalDate date) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter w = new PrintWriter(baos)) {
            // ── Заголовок ─────────────────────────────────────────────────────
            w.println("ОТЧЁТ ЗА " + date.format(DATE_FMT));
            w.println("Example Bank");
            w.println();

            // ── Платежи ───────────────────────────────────────────────────────
            w.println("=== ПЛАТЕЖИ ===");
            w.println("№;Платёж ID;Клиент ID;Сумма;Валюта;Статус;Получатель;Дата;Время");
            List<PaymentReport> payments = PaymentReport.findByDate(date);
            int row = 1;
            for (PaymentReport p : payments) {
                w.printf("%d;%d;%d;%.2f;%s;%s;%s;%s;%s%n",
                    row++, p.getPaymentId(), p.getClientId(),
                    p.getAmount(), p.getCurrency(),
                    translateStatus(p.getStatus()),
                    orEmpty(p.getRecipientAccount()),
                    p.getCreatedAt().format(DATE_FMT),
                    p.getCreatedAt().format(TIME_FMT));
            }
            w.printf("Итого платежей: %d%n%n", payments.size());

            // ── Переводы ──────────────────────────────────────────────────────
            w.println("=== ПЕРЕВОДЫ ===");
            w.println("№;Перевод ID;Клиент ID;Тип;Сумма;Валюта;Статус;Получатель;Дата;Время");
            List<TransferReport> transfers = TransferReport.findByDate(date);
            row = 1;
            for (TransferReport t : transfers) {
                w.printf("%d;%d;%d;%s;%.2f;%s;%s;%s;%s;%s%n",
                    row++, t.getTransferId(), t.getClientId(),
                    t.getTransferType(), t.getAmount(), t.getCurrency(),
                    translateStatus(t.getStatus()),
                    orEmpty(t.getRecipientDisplay()),
                    t.getOccurredAt().format(DATE_FMT),
                    t.getOccurredAt().format(TIME_FMT));
            }
            w.printf("Итого переводов: %d%n%n", transfers.size());

            // ── Итоги ─────────────────────────────────────────────────────────
            w.println("=== ИТОГИ ===");
            BigDecimal totalPayments = payments.stream()
                .map(PaymentReport::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTransfers = transfers.stream()
                .map(TransferReport::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            w.printf("Всего платежей: %d на сумму %.2f%n", payments.size(), totalPayments);
            w.printf("Всего переводов: %d на сумму %.2f%n", transfers.size(), totalTransfers);
            w.println("Отчёт сформирован: " + LocalDate.now().format(DATE_FMT));
        }
        return baos.toByteArray();
    }

    // ── Здоровье партиций ─────────────────────────────────────────────────────

    public String checkPartitionHealth() {
        StringBuilder result = new StringBuilder();
        String sql = """
            SELECT tablename,
                   pg_size_pretty(pg_total_relation_size('report.'||tablename)) AS size
            FROM pg_tables
            WHERE schemaname = 'report'
              AND (tablename LIKE 'payment_reports%' OR tablename LIKE 'transfer_reports%')
            ORDER BY tablename DESC
            LIMIT 20
            """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.append(rs.getString("tablename"))
                      .append(": ").append(rs.getString("size")).append("\n");
            }
        } catch (Exception e) {
            LOG.error("Failed to check partition health", e);
            return "Error: " + e.getMessage();
        }
        return result.isEmpty() ? "No partitions found" : result.toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private PeriodSummaryDto buildSummary(List<PaymentReport> payments,
                                           List<TransferReport> transfers,
                                           String periodLabel) {
        BigDecimal totalP = payments.stream()
            .map(PaymentReport::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalT = transfers.stream()
            .map(TransferReport::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PeriodSummaryDto(
            payments.size(), transfers.size(), totalP, totalT, "RUB", periodLabel);
    }

    private PaymentReportDto toPaymentDto(PaymentReport p) {
        PaymentReportDto d = new PaymentReportDto();
        d.paymentId        = p.getPaymentId();
        d.amount           = p.getAmount();
        d.currency         = p.getCurrency();
        d.status           = p.getStatus();
        d.recipientAccount = p.getRecipientAccount();
        d.reportDate       = p.getReportDate();
        d.createdAt        = p.getCreatedAt();
        return d;
    }

    private TransferReportDto toTransferDto(TransferReport t) {
        TransferReportDto d = new TransferReportDto();
        d.transferId       = t.getTransferId();
        d.transferType     = t.getTransferType();
        d.status           = t.getStatus();
        d.amount           = t.getAmount();
        d.currency         = t.getCurrency();
        d.recipientDisplay = t.getRecipientDisplay();
        d.reason           = t.getReason();
        d.reportDate       = t.getReportDate();
        d.occurredAt       = t.getOccurredAt();
        return d;
    }

    private String translateStatus(String status) {
        return switch (status != null ? status : "") {
            case "CREATED"    -> "Создан";
            case "PROCESSING" -> "В обработке";
            case "COMPLETED"  -> "Завершён";
            case "FAILED"     -> "Ошибка";
            case "CANCELLED"  -> "Отменён";
            case "BLOCKED"    -> "Заблокирован";
            default           -> status != null ? status : "";
        };
    }

    private String orEmpty(String s) { return s != null ? s : ""; }

    private void validateClientId(Long clientId) {
        if (clientId == null || clientId <= 0)
            throw new IllegalArgumentException("clientId must be a positive Long (= Profile.id)");
    }
}
