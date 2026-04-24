package com.bank.report.dto;

import java.math.BigDecimal;

/**
 * Сводка операций за период (день / неделя / месяц).
 * Возвращается эндпоинтами отчётности.
 */
public class PeriodSummaryDto {

    public long    paymentCount;
    public long    transferCount;
    public BigDecimal totalPaymentAmount;
    public BigDecimal totalTransferAmount;
    public String  currency;
    public String  period;       // "2026-04-21" / "2026-W16" / "2026-04"

    public PeriodSummaryDto() {}

    public PeriodSummaryDto(long paymentCount, long transferCount,
                             BigDecimal totalPaymentAmount, BigDecimal totalTransferAmount,
                             String currency, String period) {
        this.paymentCount        = paymentCount;
        this.transferCount       = transferCount;
        this.totalPaymentAmount  = totalPaymentAmount;
        this.totalTransferAmount = totalTransferAmount;
        this.currency            = currency;
        this.period              = period;
    }
}
