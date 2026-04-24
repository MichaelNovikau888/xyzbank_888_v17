package com.bank.profile.service.impl;

import com.bank.profile.entity.Audit;
import com.bank.profile.kafka.producer.AuditProducer;
import com.bank.profile.mapper.AuditMapper;
import com.bank.profile.repository.AuditRepository;
import com.bank.profile.service.AuditService;
import com.bank.profile.util.audit.EntityType;
import com.bank.profile.util.audit.OperationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * В Spring propagation = REQUIRES_NEW давал новую транзакцию независимо от вызывающей.
 * В Quarkus/Jakarta для такого же поведения используется
 * @Transactional(Transactional.TxType.REQUIRES_NEW).
 */
@ApplicationScoped
public class AuditServiceImpl implements AuditService {

    private static final Logger log = Logger.getLogger(AuditServiceImpl.class);

    @Inject AuditRepository auditRepository;
    @Inject AuditProducer auditProducer;
    @Inject AuditMapper auditMapper;
    @Inject ObjectMapper objectMapper;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void create(Object value) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize entity for audit", e);
        }

        String entityTypeName;
        try {
            entityTypeName = EntityType.valueOf(value.getClass().getSimpleName()).toString();
        } catch (IllegalArgumentException e) {
            log.warnf("Unknown EntityType for class=%s, using None", value.getClass().getSimpleName());
            entityTypeName = EntityType.None.toString();
        }
        Audit entity = new Audit(
                null,
                entityTypeName,
                OperationType.Create.toString(),
                "user",
                null,
                LocalDateTime.now(),
                null,
                null,
                json
        );
        auditRepository.persist(entity);
        auditProducer.sendAudit(auditMapper.toDto(entity));
        log.infof("Audit CREATE: entityType=%s, id=%d", entity.getEntityType(), entity.getId());
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void update(Object value) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize entity for audit", e);
        }

        Pattern pattern = Pattern.compile("(\"id\":-?\\d+,)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return;
        String idFragment = matcher.group(1);

        String entityTypeForUpdate;
        try {
            entityTypeForUpdate = EntityType.valueOf(value.getClass().getSimpleName()).toString();
        } catch (IllegalArgumentException e) {
            log.warnf("Unknown EntityType for class=%s, skipping audit update", value.getClass().getSimpleName());
            return;
        }
        auditRepository.findByEntityTypeAndEntityJsonContains(
                entityTypeForUpdate,
                idFragment
        ).ifPresent(audit -> {
            audit.setModifiedBy("user");
            audit.setModifiedAt(LocalDateTime.now());
            audit.setNewEntityJson(json);
            auditRepository.persist(audit);
            auditProducer.sendAudit(auditMapper.toDto(audit));
            log.infof("Audit UPDATE: entityType=%s, id=%d", audit.getEntityType(), audit.getId());
        });
    }
}
