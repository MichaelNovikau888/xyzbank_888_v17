package com.bank.account.service;

import com.bank.account.enums.OperationType;
import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.mapper.AuditMapper;
import com.bank.account.repository.AuditRepository;
import com.bank.account.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    @Value("${audit.entity-type}")
    private String entityType;

    @Value("${audit.default-user}")
    private String currentUser;

    private final AuditRepository auditRepository;
    private final AuditMapper auditMapper;

    @Override
    @Transactional
    public AuditDto createAudit(Object result) throws JsonProcessingException {
        final AccountDto accountDto = (AccountDto) result;
        try {
            final String entityJson = JsonUtils.convertToJson(accountDto);

            final AuditDto auditDto = AuditDto.builder()
                    .entityType(entityType)
                    .operationType(OperationType.CREATE.name())
                    .createdBy(currentUser)
                    .modifiedBy(null)
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .modifiedAt(null)
                    .newEntityJson(null)
                    .entityJson(entityJson)
                    .build();
            final Audit audit = auditRepository.save(auditMapper.toAudit(auditDto));

            log.info("Audit log successfully saved: {}", audit);
            return auditDto;
        } catch (JsonProcessingException e) {
            log.error("Failed to convert account to JSON for audit. Account ID: {}",
                    accountDto.getId(), e);
            throw new JsonProcessingException(
                    String.format("Failed to create audit for account %d due to JSON processing error",
                            accountDto.getId()), e);
        }
    }

    @Override
    @Transactional
    public AuditDto updateAudit(Object result) throws JsonProcessingException {
        final AccountDto accountDto = (AccountDto) result;
        try {
            final String newEntityJson = JsonUtils.convertToJson(accountDto);
            final Long accountId = accountDto.getId();
            final AuditDto oldAuditDto = getAuditByEntityId(accountId);
            final String oldEntityJson = Optional.ofNullable(oldAuditDto.getNewEntityJson())
                    .orElse(oldAuditDto.getEntityJson());
            final AuditDto auditDto = AuditDto.builder()
                    .entityType(oldAuditDto.getEntityType())
                    .operationType(OperationType.UPDATE.name())
                    .createdBy(oldAuditDto.getCreatedBy())
                    .modifiedBy(currentUser)
                    .createdAt(oldAuditDto.getCreatedAt())
                    .modifiedAt(new Timestamp(System.currentTimeMillis()))
                    .newEntityJson(newEntityJson)
                    .entityJson(oldEntityJson)
                    .build();

            auditRepository.save(auditMapper.toAudit(auditDto));
            log.info("Audit log successfully updated: {}", auditDto);
            return auditDto;
        }  catch (JsonProcessingException e) {
            final String errorMessage = "Failed to convert account to JSON for audit.";
            log.error(errorMessage + " Account ID: {}", accountDto.getId(), e);
            throw new JsonProcessingException(errorMessage, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuditDto getAuditByEntityId(Long entityIdFromCurrentAccount) {
        try {
            final String searchPattern = "\"id\"\\s*:\\s*" + entityIdFromCurrentAccount + "\\b";
            final Pattern pattern = Pattern.compile(searchPattern);
            final List<Audit> auditList = auditRepository.findAll()
                    .stream()
                    .filter(audit -> pattern.matcher(audit.getEntityJson()).find())
                    .toList();

            if (auditList.isEmpty()) {
                throw new EntityNotFoundException("Audit not found for entity ID: " +
                        entityIdFromCurrentAccount);
            }

            if (auditList.size() == 1) {
                return auditMapper.toAuditDto(auditList.get(0));
            }

            return auditList.stream()
                    .filter(audit -> audit.getModifiedAt() != null)
                    .max(Comparator.comparing(Audit::getModifiedAt))
                    .map(auditMapper::toAuditDto)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No modified audit records found for entity ID: " + entityIdFromCurrentAccount
                    ));
        } catch (Exception e) {
            log.error("Failed to retrieve audit for entity ID: {}", entityIdFromCurrentAccount, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditDto> getAllAudits() {
        final List<AuditDto> resultAuditDtoList = auditRepository.findAll()
                .stream()
                .map(auditMapper::toAuditDto)
                .toList();

        log.info("Successfully retrieved all audits");
        return resultAuditDtoList;
    }
}
