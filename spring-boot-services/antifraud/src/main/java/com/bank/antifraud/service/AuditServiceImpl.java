package com.bank.antifraud.service;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.globalException.DataAccessException;
import com.bank.antifraud.mappers.AuditMapper;
import com.bank.antifraud.model.Audit;
import com.bank.antifraud.repository.AuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;
    private final AuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AuditDto createAudit(String operationType, String entityType,
                                String createdBy, Object newEntity,
                                Object oldEntity) {
        try {
            Audit audit = Audit.builder()
                    .entityType(entityType)
                    .operationType(operationType)
                    .createdBy(createdBy)
                    .modifiedBy(null)
                    .createdAt(LocalDateTime.now())
                    .modifiedAt(null)
                    .newEntityJson(objectMapper.writeValueAsString(newEntity))
                    .entityJson(oldEntity != null ?
                            objectMapper.writeValueAsString(oldEntity) :
                            "{}")
                    .build();

            Audit savedAudit = auditRepository.save(audit);
            return auditMapper.toAuditDTO(savedAudit);
        } catch (Exception e) {
            log.error("Failed to create audit record: {}", e.getMessage(), e);
            throw new RuntimeException("Audit creation failed", e);
        }
    }

    @Override
    @Transactional
    public AuditDto updateAudit(long id, AuditDto auditDto) {
        Audit audit = auditRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit not found with ID: " + id));
        audit.setEntityType(auditDto.getEntityType());
        audit.setOperationType(auditDto.getOperationType());
        audit.setModifiedBy(auditDto.getModifiedBy());
        audit.setModifiedAt(LocalDateTime.now());
        audit.setNewEntityJson(auditDto.getNewEntityJson());
        audit.setEntityJson(auditDto.getEntityJson());
        auditRepository.save(audit);
        return auditMapper.toAuditDTO(audit);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditDto getAuditById(long id) {
        return auditRepository.findById(id)
                .map(auditMapper::toAuditDTO)
                .orElseThrow(() -> new RuntimeException("Audit not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditDto> getAllAudits() {
        try {
            // CrudRepository.findAll() возвращает Iterable, конвертируем в List
            return StreamSupport.stream(auditRepository.findAll().spliterator(), false)
                    .map(auditMapper::toAuditDTO)
                    .collect(Collectors.toList());
        } catch (DataAccessException e) {
            log.error("Database error while retrieving all audit DTOs:", e);
            throw new DataAccessException("Failed to retrieve all audit DTOs due to database error: " + e);
        } catch (Exception e) {
            log.error("Failed to retrieve all audit DTOs:", e);
            throw new RuntimeException("Unexpected error while retrieving all audit DTOs", e);
        }
    }

    @Override
    @Transactional
    public void deleteAudit(long id) {
        // id — примитив long, передаётся напрямую. Никакого .longValue() — это метод объекта Long.
        auditRepository.deleteById(id);
    }
}
