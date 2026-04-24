package com.bank.antifraud.aspect;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.kafkaProducer.AuditProducer;
import com.bank.antifraud.service.AuditService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private AuditProducer auditProducer;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private Signature signature;

    @InjectMocks
    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("analyzeAccountTransfer");
    }

    @Test
    void logAuditAnalyzeResult_shouldCreateAndSendAudit_whenResultNotNull() {
        Object testResult = new Object();
        AuditDto expectedAudit = new AuditDto();
        when(auditService.createAudit(
                eq("CREATE"),
                eq("Object"),
                eq("anti_fraud_system"),
                eq(null),
                eq(testResult)
        )).thenReturn(expectedAudit);

        auditAspect.logAuditAnalyzeResult(joinPoint, testResult);

        verify(auditService).createAudit(
                "CREATE", "Object", "anti_fraud_system", null, testResult);
        verify(auditProducer).sendAuditLog(expectedAudit);
    }

    @Test
    void logAuditAnalyzeResult_shouldNotCreateAudit_whenResultIsNull() {
        auditAspect.logAuditAnalyzeResult(joinPoint, null);

        verifyNoInteractions(auditService);
        verifyNoInteractions(auditProducer);
    }

    @Test
    void logAuditAnalyzeResult_shouldLogError_whenExceptionOccurs() {
        Object testResult = new Object();
        when(auditService.createAudit(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test exception"));

        auditAspect.logAuditAnalyzeResult(joinPoint, testResult);

        verify(auditService).createAudit(any(), any(), any(), any(), any());
        verifyNoInteractions(auditProducer);
    }
}
