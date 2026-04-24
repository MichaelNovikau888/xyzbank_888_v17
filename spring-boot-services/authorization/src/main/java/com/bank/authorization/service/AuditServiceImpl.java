package com.bank.authorization.service;

import com.bank.authorization.dto.AuditDto;
import com.bank.authorization.dto.UserDto;
import com.bank.authorization.entity.Audit;
import com.bank.authorization.entity.EntityType;
import com.bank.authorization.entity.OperationType;
import com.bank.authorization.entity.User;
import com.bank.authorization.mapper.AuditMapper;
import com.bank.authorization.repository.AuditRepository;
import com.bank.authorization.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final String SYSTEM_USER = "SYSTEM";

    private final AuditRepository auditRepository;
    private final AuditMapper auditMapper;

    @Override
    @Transactional
    public void logUserCreation(UserDto userDto) {
        AuditDto auditDto = new AuditDto();
        auditDto.setEntityType(EntityType.USER.name());
        auditDto.setOperationType(OperationType.CREATE.name());
        auditDto.setCreatedBy(SYSTEM_USER);
        auditDto.setEntityJson(JsonUtils.toJson(userDto));
        auditDto.setCreatedAt(LocalDateTime.now());

        save(auditDto);
    }

    @Override
    @Transactional
    public void logUserUpdate(Long userId, UserDto userDto) {
        final List<Audit> audits = auditRepository.findAll();

        Audit existingAudit = null;

        for (Audit audit : audits) {
            try {
                final User user = JsonUtils.fromJson(audit.getEntityJson(), User.class);
                if (user != null && user.getId() != null && user.getId().equals(userId)) {
                    existingAudit = audit;
                    break;
                }
            } catch (Exception e) {
                log.error("Ошибка при разборе JSON из audit.entity_json, ID записи: {}, ошибка: {}",
                        audit.getId(), e.getMessage(), e);
            }
        }

        if (existingAudit != null) {
            existingAudit.setOperationType(OperationType.UPDATE.name());
            existingAudit.setModifiedBy(SYSTEM_USER);
            existingAudit.setModifiedAt(LocalDateTime.now());
            existingAudit.setNewEntityJson(JsonUtils.toJson(userDto));
            auditRepository.save(existingAudit);
        } else {
            log.error("Не найдена запись аудита для пользователя с ID: {}", userDto.getId());
        }
    }

    @Override
    @Transactional
    public void save(AuditDto auditDto) {
        Audit audit = auditMapper.toEntity(auditDto);
        auditRepository.save(audit);
    }
}
