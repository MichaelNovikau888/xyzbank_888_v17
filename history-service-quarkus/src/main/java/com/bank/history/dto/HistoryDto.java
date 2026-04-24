package com.bank.history.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HistoryDto {
    private Long id;
    private Long transferAuditId;
    private Long profileAuditId;
    private Long accountAuditId;
    private Long antiFraudAuditId;
    private Long publicBankInfoAuditId;
    private Long authorizationAuditId;
    private String eventType;
    private String eventData;
    private LocalDateTime createdAt;
    private String serviceName;
}
