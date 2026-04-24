package com.bank.account.unit.service;

import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import com.bank.account.exception.custom_exceptions.DataAccessException;
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
public class AuditServiceGetAllTest {

    @Mock
    private AuditRepository auditRepositoryMock;

    @Mock
    private AuditMapper auditMapperMock;

    @InjectMocks
    private AuditServiceImpl auditServiceMock;

    private Audit auditFirst;
    private Audit auditSecond;
    private AuditDto auditDtoSavedFirst;
    private AuditDto auditDtoSavedSecond;

    @BeforeEach
    void setUp() {
        auditFirst = TestUtils.createAudit(
                1L,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\":1, \"money\":600.00}",
                null
        );

        auditSecond = TestUtils.createAudit(
                2L,
                "Account",
                "UPDATING",
                "SYSTEM",
                "SYSTEM",
                new Timestamp(System.currentTimeMillis() - 100000),
                new Timestamp(System.currentTimeMillis()),
                "{\"id\":1, \"money\":600.00}",
                "{\"id\":1, \"money\":900.00}"
        );

        auditDtoSavedFirst = TestUtils.createAuditDto(
                1L,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\":1, \"money\":600.00}",
                null
        );

        auditDtoSavedSecond = TestUtils.createAuditDto(
                2L,
                "Account",
                "UPDATING",
                "SYSTEM",
                "SYSTEM",
                new Timestamp(System.currentTimeMillis() - 100000),
                new Timestamp(System.currentTimeMillis()),
                "{\"id\":1, \"money\":600.00}",
                "{\"id\":1, \"money\":900.00}"
        );
    }

    @Test
    void getAllAudits_Success() {
        List<Audit> auditList = List.of(auditFirst, auditSecond);
        List<AuditDto> expectedAuditDtoList = List.of(auditDtoSavedFirst, auditDtoSavedSecond);

        when(auditRepositoryMock.findAll()).thenReturn(auditList);
        when(auditMapperMock.toAuditDto(any(Audit.class))).thenAnswer(invocation -> {
            Audit audit = invocation.getArgument(0);
            return new AuditDto(
                    audit.getId(),
                    audit.getEntityType(),
                    audit.getOperationType(),
                    audit.getCreatedBy(),
                    audit.getModifiedBy(),
                    audit.getCreatedAt(),
                    audit.getModifiedAt(),
                    audit.getNewEntityJson(),
                    audit.getEntityJson()
            );
        });

        List<AuditDto> result = auditServiceMock.getAllAudits();

        assertNotNull(result);
        assertEquals(expectedAuditDtoList.size(), result.size());
        assertEquals(expectedAuditDtoList, result);

        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, times(auditList.size())).toAuditDto(any(Audit.class));
    }

    @Test
    void getAllAudits_EmptyList() {
        when(auditRepositoryMock.findAll()).thenReturn(Collections.emptyList());

        List<AuditDto> result = auditServiceMock.getAllAudits();

        assertNotNull(result);
        assertEquals(0, result.size());

        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, never()).toAuditDto(any(Audit.class));
    }

    @Test
    void getAllAudits_DataAccessException() {
        when(auditRepositoryMock.findAll())
                .thenThrow(new DataAccessException("Failed to retrieve audits due to database error"));

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            auditServiceMock.getAllAudits();
        });

        assertEquals("Failed to retrieve audits due to database error", exception.getMessage());
        verify(auditRepositoryMock, times(1)).findAll();
        verify(auditMapperMock, never()).toAuditDto(any(Audit.class));
    }
}
