package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.mapper.AuditMapper;
import com.bank.account.repository.AuditRepository;
import com.bank.account.service.AuditServiceImpl;
import com.bank.account.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceGetAuditByEntityIdTest {

    @Mock
    private AuditRepository auditRepositoryMock;

    @Mock
    private AuditMapper auditMapperMock;

    @InjectMocks
    private AuditServiceImpl auditServiceMock;

    private AccountDto accountDtoSaved;
    private Audit audit;
    private AuditDto auditDto;
    private AuditDto auditDtoSaved;

    @BeforeEach
    void setUp() {
        auditDto = TestUtils.createAuditDto(
                null,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\":1}",
                null
        );

        audit = TestUtils.createAudit(
                1L,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\":1}",
                null
        );

        accountDtoSaved = TestUtils.createAccountDto(
                1L,
                156L,
                "7654321",
                new BigDecimal("300.00"),
                false,
                154L,
                154L
        );

        auditDtoSaved = TestUtils.createAuditDto(
                1L,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\":1}",
                null
        );
    }

    @Test
    void getAuditByEntityId_Success() {
        when(auditRepositoryMock.findAll()).thenReturn(List.of(audit));
        when(auditMapperMock.toAuditDto(audit)).thenReturn(auditDtoSaved);

        AuditDto result = auditServiceMock.getAuditByEntityId(1L);

        assertNotNull(result);
        assertEquals(auditDtoSaved.getEntityJson(), result.getEntityJson());
        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, times(1)).toAuditDto(audit);
    }

    @Test
    void getAuditByEntityId_EmptyList() {
        when(auditRepositoryMock.findAll()).thenReturn(Collections.emptyList());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> auditServiceMock.getAuditByEntityId(1L));

        assertEquals("Audit not found for entity ID: 1", exception.getMessage());
        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, never()).toAuditDto(any());
    }

    @Test
    void getAuditByEntityId_NoMatchingEntityId() {
        Audit nonMatchingAudit = new Audit();
        nonMatchingAudit.setId(3L);
        nonMatchingAudit.setEntityType("Account");
        nonMatchingAudit.setOperationType("CREATION");
        nonMatchingAudit.setCreatedBy("SYSTEM");
        nonMatchingAudit.setModifiedBy("");
        nonMatchingAudit.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        nonMatchingAudit.setModifiedAt(null);
        nonMatchingAudit.setNewEntityJson(null);
        nonMatchingAudit.setEntityJson("{\"id\":999}");

        when(auditRepositoryMock.findAll()).thenReturn(List.of(nonMatchingAudit));

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> auditServiceMock.getAuditByEntityId(1L));

        assertEquals("Audit not found for entity ID: 1", exception.getMessage());
        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, never()).toAuditDto(any());
    }

    @Test
    void getAuditByEntityId_DataAccessException() {
        when(auditRepositoryMock.findAll())
                .thenThrow(new RuntimeException("Database error while retrieving audits"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> auditServiceMock.getAuditByEntityId(1L));

        assertEquals("Database error while retrieving audits", exception.getMessage());
        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, never()).toAuditDto(any());
    }
}
