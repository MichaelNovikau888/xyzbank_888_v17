package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.AuditDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.entity.Audit;
import com.bank.publicinfo.enumtype.EntityType;
import com.bank.publicinfo.enumtype.OperationType;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.mapper.AuditMapper;
import com.bank.publicinfo.producer.AuditProducer;
import com.bank.publicinfo.repository.AuditRepository;
import com.bank.publicinfo.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Реализация сервиса аудита для public-info.
 */
 /**
 * Изменения относительно первоначальной версии:
 *   1. Транзакция REQUIRES_NEW — аудит записывается независимо от бизнес-транзакции.
 *      Откат бизнес-метода не откатывает уже записанный аудит.
 *   2. Kafka: после persist() → AuditProducer.sendAudit() → топик audit.logs
 *      → history-service сохраняет изменения в историю.
 */
@ApplicationScoped
public class AuditServiceImpl implements AuditService {

    private static final Logger LOG = Logger.getLogger(AuditServiceImpl.class);

    @Inject AuditMapper     mapper;
    @Inject AuditRepository repository;
    @Inject ObjectMapper    objectMapper;
    @Inject AuditProducer   auditProducer;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public <T> void createAudit(T dto) {
        try {
            Audit audit = new Audit();
            audit.setEntityType(EntityType.from(dto.getClass().getSimpleName()).name());
            audit.setOperationType(OperationType.CREATE.name());
            audit.setCreatedBy("SYSTEM");
            audit.setCreatedAt(LocalDateTime.now());
            audit.setEntityJson(objectMapper.writeValueAsString(dto));
            repository.persist(audit);
            auditProducer.sendAudit(mapper.toDto(audit));
            LOG.debugf("Audit CREATE: %s id=%d", audit.getEntityType(), audit.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to create audit for %s", dto.getClass().getSimpleName());
        }
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public <T> void updateAudit(T dto) {
        try {
            Audit audit = new Audit();
            audit.setEntityType(EntityType.from(dto.getClass().getSimpleName()).name());
            audit.setOperationType(OperationType.UPDATE.name());
            audit.setCreatedBy("SYSTEM");
            audit.setModifiedBy("SYSTEM");
            audit.setCreatedAt(LocalDateTime.now());
            audit.setModifiedAt(LocalDateTime.now());
            audit.setEntityJson(objectMapper.writeValueAsString(dto));
            repository.persist(audit);
            auditProducer.sendAudit(mapper.toDto(audit));
            LOG.debugf("Audit UPDATE: %s id=%d", audit.getEntityType(), audit.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to update audit for %s", dto.getClass().getSimpleName());
        }
    }

    @Override
    public PagedResponse<AuditDto> getAll(int page, int size) {
        List<AuditDto> content = repository.findAll()
                .page(page, size).list()
                .stream().map(mapper::toDto).toList();
        return new PagedResponse<>(content, page, size, repository.count());
    }

    @Override
    public AuditDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("Audit ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Audit not found: " + id)));
    }

    @Override
    public AuditDto getByEntityType(String entityType) {
        if (entityType == null || entityType.isBlank())
            throw new IllegalArgumentException("entityType must not be blank");
        return mapper.toDto(
            repository.findByEntityType(entityType)
                .orElseThrow(() -> new EntityNotFoundException(
                    "No audit record found for entityType: " + entityType))
        );
    }

    @Override
    public List<AuditDto> getAllByEntityJson(String entityJson) {
        if (entityJson == null || entityJson.isBlank())
            throw new IllegalArgumentException("entityJson must not be blank");
        return repository.findAllByEntityJson(entityJson)
                .stream().map(mapper::toDto).toList();
    }
}