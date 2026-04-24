package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Audit;
import com.bank.account.exception.custom_exceptions.DataAccessException;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.mapper.AuditMapper;
import com.bank.account.repository.AuditRepository;
import com.bank.account.service.AccountServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceCreateTest {

    @Mock
    private AuditRepository auditRepositoryMock;

    @Mock
    private AuditMapper auditMapperMock;

    @InjectMocks
    private AuditServiceImpl auditServiceMock;

    private AccountDto accountDtoSaved;
    private Audit audit;
    private AuditDto auditDto;

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
                "{}",
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
                "{}",
                null
        );

        accountDtoSaved = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);
    }

    @Test
    void createAuditTest_Success() throws JsonProcessingException {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{}");
            when(auditMapperMock.toAudit(any(AuditDto.class))).thenReturn(audit);
            when(auditRepositoryMock.save(any(Audit.class))).thenReturn(audit);

            AuditDto result = auditServiceMock.createAudit(accountDtoSaved);

            assertNotNull(result);
            assertEquals("CREATE", result.getOperationType());
            verify(auditRepositoryMock, times(1)).save(any(Audit.class));
        }
    }

    @Test
    void createAuditTest_JsonProcessingException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class)))
                    .thenThrow(new JsonProcessingException("Failed to convert object to JSON"));

            JsonProcessingException exception = assertThrows(JsonProcessingException.class, () -> {
                auditServiceMock.createAudit(accountDtoSaved);
            });

            assertEquals("Failed to create audit for account 1 due to JSON processing error",
                    exception.getMessage());
        }
    }

    @Test
    void createAuditTest_DataAccessException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{}");
            when(auditMapperMock.toAudit(any(AuditDto.class))).thenReturn(audit);
            when(auditRepositoryMock.save(any(Audit.class)))
                    .thenThrow(new DataAccessException("Failed to create audit DTO due to database error"));

            assertThrows(DataAccessException.class, () -> auditServiceMock.createAudit(accountDtoSaved));
        }
    }

    @Test
    void createAuditTest_RuntimeException() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.convertToJson(any(AccountDto.class))).thenReturn("{}");
            when(auditRepositoryMock.save(any(Audit.class)))
                    .thenThrow(new RuntimeException("Unexpected error while creating audit DTO"));

            assertThrows(RuntimeException.class, () -> auditServiceMock.createAudit(accountDtoSaved));
        }
    }
}
