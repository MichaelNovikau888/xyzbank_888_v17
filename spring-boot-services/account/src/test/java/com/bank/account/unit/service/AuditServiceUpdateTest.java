package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import com.bank.account.exception.custom_exceptions.DataAccessException;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.mapper.AuditMapper;
import com.bank.account.repository.AuditRepository;
import com.bank.account.service.AuditServiceImpl;
import com.bank.account.utils.JsonUtils;
import com.bank.account.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceUpdateTest {

    @Mock
    private AuditRepository auditRepositoryMock;

    @Mock
    private AuditMapper auditMapperMock;

    @InjectMocks
    private AuditServiceImpl auditServiceMock;

    private AccountDto accountDtoSaved;
    private Audit audit;
    private AuditDto auditDtoSaved;
    private AuditDto auditDtoUpdated;
    private Audit auditUpdated;

    @BeforeEach
    void setUp() {
        audit = TestUtils.createAudit(
                1L,
                "Account",
                "CREATION",
                "SYSTEM",
                "",
                new Timestamp(System.currentTimeMillis()),
                null,
                "{\"id\": 1}",
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
                "{\"id\": 1}",
                null
        );

        auditDtoUpdated = TestUtils.createAuditDto(
                1L,
                "Account",
                "UPDATING",
                "SYSTEM",
                "SYSTEM",
                auditDtoSaved.getCreatedAt(),
                new Timestamp(System.currentTimeMillis()),
                auditDtoSaved.getEntityJson(),
                "{\"id\": 1}"
        );

        auditUpdated = TestUtils.createAudit(
                1L,
                "Account",
                "UPDATING",
                "SYSTEM",
                "SYSTEM",
                auditDtoSaved.getCreatedAt(),
                new Timestamp(System.currentTimeMillis()),
                auditDtoSaved.getEntityJson(),
                "{\"id\": 1}"
        );
    }

    @Test
    void updateAuditTest_Success() throws JsonProcessingException {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            // Arrange
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{\"id\": 1}");
            when(auditRepositoryMock.findAll()).thenReturn(List.of(audit));
            when(auditMapperMock.toAuditDto(audit)).thenReturn(auditDtoSaved);
            when(auditMapperMock.toAudit(any(AuditDto.class))).thenReturn(audit);
            doReturn(audit).when(auditRepositoryMock).save(any(Audit.class));

            AuditDto result = auditServiceMock.updateAudit(accountDtoSaved);

            assertNotNull(result);
            assertEquals("UPDATE", result.getOperationType());
            assertEquals(auditDtoSaved.getCreatedAt(), result.getCreatedAt());
            assertNotNull(result.getModifiedAt());
            assertEquals("{\"id\": 1}", result.getNewEntityJson());
            assertEquals("{\"id\": 1}", result.getEntityJson());

            verify(auditRepositoryMock, times(1)).findAll();
            verify(auditRepositoryMock, times(1)).save(any(Audit.class));
        }
    }


    @Test
    void updateAuditTest_JsonProcessingException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class)))
                    .thenThrow(new JsonProcessingException("Failed to convert account to JSON for audit."));

            JsonProcessingException exception = assertThrows(JsonProcessingException.class, () -> {
                auditServiceMock.updateAudit(accountDtoSaved);
            });

            assertEquals("Failed to convert account to JSON for audit.", exception.getMessage());
        }
    }

    @Test
    void updateAuditTest_EntityNotFoundException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{\"id\": 1}");
            when(auditRepositoryMock.findAll()).thenReturn(List.of());

            EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> {
                auditServiceMock.updateAudit(accountDtoSaved);
            });

            assertEquals("Audit not found for entity ID: 1", exception.getMessage());
        }
    }

    @Test
    void updateAuditTest_DataAccessException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class)))
                    .thenReturn("{\"id\": 1}");
            
            when(auditRepositoryMock.findAll()).thenReturn(List.of(audit));
            when(auditMapperMock.toAuditDto(audit)).thenReturn(auditDtoSaved);
            when(auditMapperMock.toAudit(any(AuditDto.class))).thenReturn(auditUpdated);
            doThrow(new DataAccessException("Database error")).when(auditRepositoryMock).save(any(Audit.class));

            DataAccessException exception = assertThrows(DataAccessException.class, () -> {
                auditServiceMock.updateAudit(accountDtoSaved);
            });

            assertEquals("Database error", exception.getMessage());
            verify(auditRepositoryMock, times(1)).findAll();
            verify(auditRepositoryMock, times(1)).save(any(Audit.class));
        }
    }

    @Test
    void updateAuditTest_RuntimeException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{\"id\": 1}");
            when(auditRepositoryMock.findAll()).thenReturn(List.of(audit));
            when(auditMapperMock.toAuditDto(audit)).thenReturn(auditDtoSaved);
            when(auditMapperMock.toAudit(any(AuditDto.class))).thenReturn(auditUpdated);
            doThrow(new RuntimeException("Unexpected error")).when(auditRepositoryMock).save(any(Audit.class));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                auditServiceMock.updateAudit(accountDtoSaved);
            });

            assertEquals("Unexpected error", exception.getMessage());
            verify(auditRepositoryMock, times(1)).findAll();
            verify(auditRepositoryMock, times(1)).save(any(Audit.class));
        }
    }
}
