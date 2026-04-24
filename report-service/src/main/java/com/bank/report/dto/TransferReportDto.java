package com.bank.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO записи перевода. */
public class TransferReportDto {
    public Long transferId;
    public String transferType;
    public String status;
    public BigDecimal amount;
    public String currency;
    public String recipientDisplay;
    public String reason;
    public LocalDate reportDate;
    public LocalDateTime occurredAt;
}
