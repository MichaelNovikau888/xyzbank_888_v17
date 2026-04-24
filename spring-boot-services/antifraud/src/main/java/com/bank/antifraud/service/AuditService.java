package com.bank.antifraud.service;

import com.bank.antifraud.dto.AuditDto;

import java.util.List;

public interface AuditService {
    AuditDto createAudit(String operationType, String entityType,
                         String createdBy, Object newEntity,
                         Object oldEntity);

    AuditDto updateAudit(long id, AuditDto auditDto);

    AuditDto getAuditById(long id);

    void deleteAudit(long id);

    List<AuditDto> getAllAudits();
}
