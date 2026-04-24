package com.bank.account.service;

import com.bank.account.dto.AuditDto;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;

import java.util.List;

public interface AuditService {

    AuditDto getAuditByEntityId(Long entityIdFromCurrentAccount);

    List<AuditDto> getAllAudits();

    AuditDto createAudit(Object result) throws JsonProcessingException;

    AuditDto updateAudit(Object result) throws JsonProcessingException;
}

