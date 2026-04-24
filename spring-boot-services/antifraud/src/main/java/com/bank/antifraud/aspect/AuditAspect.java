package com.bank.antifraud.aspect;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.kafkaProducer.AuditProducer;
import com.bank.antifraud.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {
    private final String OPERATION = "CREATE";
    private final String CREATED_BY = "anti_fraud_system";

    private final AuditProducer auditProducer;
    private final AuditService auditService;

    @AfterReturning(pointcut = "execution(* com.bank.antifraud.service.SuspiciousTransferServiceImpl.analyzeAccountTransfer(..)) || " +
            "execution(* com.bank.antifraud.service.SuspiciousTransferServiceImpl.analyzePhoneTransfer(..)) || " +
            "execution(* com.bank.antifraud.service.SuspiciousTransferServiceImpl.analyzeCardTransfer(..))",
            returning = "result")
    public void logAuditAnalyzeResult(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();
            if (result == null) {
                log.warn("Audit skipped: result is null for method {}", methodName);
                return;
            }
            AuditDto auditDto = auditService.createAudit(
                    OPERATION,
                    result.getClass().getSimpleName(),
                    CREATED_BY,
                    null,
                    result
            );
            auditProducer.sendAuditLog(auditDto);
        } catch (Exception e) {
            log.error("Failed to audit operation: ", e);
        }
    }
}
