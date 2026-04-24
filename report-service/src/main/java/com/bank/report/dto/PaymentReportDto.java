package com.bank.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO платёжной записи — не содержит clientId при отдаче клиенту. */
public class PaymentReportDto {
    public Long paymentId;
    public BigDecimal amount;
    public String currency;
    public String status;
    public String recipientAccount;
    public LocalDate reportDate;
    public LocalDateTime createdAt;
}
