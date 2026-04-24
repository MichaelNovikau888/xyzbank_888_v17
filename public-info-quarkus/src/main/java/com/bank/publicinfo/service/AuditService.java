package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.dto.PagedResponse;

import java.util.List;

public interface AuditService {
    <T> void createAudit(T dto);
    <T> void updateAudit(T dto);
    PagedResponse<AuditDto> getAll(int page, int size);
    AuditDto getById(Long id);
    /** Найти первую запись аудита по типу сущности (AuditRepository.findByEntityType). */
    AuditDto getByEntityType(String entityType);
    /** Найти все записи аудита, чей entityJson содержит строку (AuditRepository.findAllByEntityJson). */
    List<AuditDto> getAllByEntityJson(String entityJson);
}
