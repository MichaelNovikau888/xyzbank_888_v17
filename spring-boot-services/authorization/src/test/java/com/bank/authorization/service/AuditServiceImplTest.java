package com.bank.authorization.service;

import com.bank.authorization.dto.AuditDto;
import com.bank.authorization.dto.UserDto;
import com.bank.authorization.entity.Audit;
import com.bank.authorization.entity.EntityType;
import com.bank.authorization.entity.OperationType;
import com.bank.authorization.entity.Role;
import com.bank.authorization.mapper.AuditMapper;
import com.bank.authorization.repository.AuditRepository;
import com.bank.authorization.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_AUDIT_ID = 1L;
    private static final String SYSTEM_USER = "SYSTEM";

    @Mock
    private AuditRepository auditRepository;
    @Mock
    private AuditMapper auditMapper;
    @InjectMocks
    private AuditServiceImpl auditService;

    private UserDto givenUserDto() {
        return UserDto.builder()
                .id(TEST_USER_ID)
                .role(Role.ROLE_USER.toString())
                .build();
    }

    private Audit givenAudit(OperationType operationType) {
        return Audit.builder()
                .id(TEST_AUDIT_ID)
                .entityType(EntityType.USER.name())
                .operationType(operationType.name())
                .createdBy(SYSTEM_USER)
                .entityJson(JsonUtils.toJson(givenUserDto()))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Audit givenInvalidJsonAudit() {
        return Audit.builder()
                .id(2L)
                .entityType(EntityType.USER.name())
                .operationType(OperationType.CREATE.name())
                .createdBy(SYSTEM_USER)
                .entityJson("invalid_json")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AuditDto givenAuditDto(OperationType operationType) {
        return AuditDto.builder()
                .entityType(EntityType.USER.name())
                .operationType(operationType.name())
                .createdBy(SYSTEM_USER)
                .entityJson(JsonUtils.toJson(givenUserDto()))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void logUserCreation_ShouldSaveCreateAudit() {
        when(auditMapper.toEntity(any(AuditDto.class)))
                .thenReturn(givenAudit(OperationType.CREATE));

        auditService.logUserCreation(givenUserDto());

        ArgumentCaptor<Audit> auditCaptor = ArgumentCaptor.forClass(Audit.class);
        verify(auditRepository).save(auditCaptor.capture());

        Audit savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getOperationType()).isEqualTo(OperationType.CREATE.name());
        assertThat(savedAudit.getEntityType()).isEqualTo(EntityType.USER.name());
        assertThat(savedAudit.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(savedAudit.getCreatedAt()).isNotNull();
        assertThat(savedAudit.getEntityJson()).isNotNull();
    }

    @Test
    void logUserUpdate_ShouldUpdateExistingAudit() {
        when(auditRepository.findAll())
                .thenReturn(List.of(givenAudit(OperationType.CREATE)));

        auditService.logUserUpdate(TEST_USER_ID, givenUserDto());

        ArgumentCaptor<Audit> auditCaptor = ArgumentCaptor.forClass(Audit.class);
        verify(auditRepository).save(auditCaptor.capture());

        Audit savedAudit = auditCaptor.getValue();
        assertThat(savedAudit.getOperationType()).isEqualTo(OperationType.UPDATE.name());
    }

    @Test
    void logUserUpdate_ShouldIgnoreInvalidJsonRecords() {
        when(auditRepository.findAll())
                .thenReturn(List.of(givenInvalidJsonAudit()));

        auditService.logUserUpdate(TEST_USER_ID, givenUserDto());

        verify(auditRepository, never()).save(any());
    }

    @Test
    void save_ShouldMapAndSaveAudit() {
        AuditDto dto = givenAuditDto(OperationType.UPDATE);
        Audit expectedAudit = givenAudit(OperationType.UPDATE);

        when(auditMapper.toEntity(dto)).thenReturn(expectedAudit);

        auditService.save(dto);

        verify(auditRepository).save(argThat(audit ->
                audit.getOperationType().equals(OperationType.UPDATE.name()) &&
                        audit.getEntityType().equals(EntityType.USER.name()) &&
                        audit.getCreatedBy().equals(SYSTEM_USER) &&
                        audit.getEntityJson().contains("\"id\":1") &&
                        audit.getEntityJson().contains("\"role\":\"ROLE_USER\"") &&
                        audit.getModifiedBy() == null &&
                        audit.getModifiedAt() == null &&
                        audit.getNewEntityJson() == null
        ));
    }

    @Test
    void logUserUpdate_ShouldNotUpdateWhenUserNotFound() {
        when(auditRepository.findAll())
                .thenReturn(List.of(givenAudit(OperationType.CREATE)));

        auditService.logUserUpdate(999L, givenUserDto());

        verify(auditRepository, never()).save(any());
    }
}
