package com.bank.account.aspects;

import com.bank.account.enums.OperationType;
import com.bank.account.dto.AccountDto;
import com.bank.account.producers.AuditProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Аспект для аудита операций с банковскими счетами.
 * Логирует создание и обновление счетов, а также отправляет события аудита.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditProducer auditProducer;

 /**
     * Логирует операцию создания нового счета.
     * Вызывается после успешного выполнения метода createNewAccount в сервисе AccountServiceImpl.
 */
 /**
     * @param result результат выполнения метода createNewAccount (созданный AccountDto)
 */
    @AfterReturning(pointcut = "execution(* com.bank.account.service.AccountServiceImpl.createNewAccount(..))",
            returning = "result")
    public void logCreateAccount(Object result) {
        try {
            final AccountDto accountDto = (AccountDto) result;
            auditProducer.sendAuditLogRequest(accountDto, OperationType.CREATE);

            log.info("Aspect 'create' completed successfully");
        } catch (Exception e) {
            log.error("Error in aspect 'create' operation: ", e);
        }
    }

 /**
     * Логирует операцию обновления существующего счета.
     * Вызывается после успешного выполнения метода updateCurrentAccount в сервисе AccountServiceImpl.
     * Метод извлекает идентификатор счета из аргументов метода, преобразует обновленные данные счета в JSON,
     * получает предыдущие данные счета из аудита, создает новую запись аудита с обновленными данными
     * и отправляет событие аудита.
 */
 /**
     * @param result результат выполнения метода updateCurrentAccount (обновленный AccountDto)
 */
    @AfterReturning(pointcut = "execution(* com.bank.account.service.AccountServiceImpl.updateCurrentAccount(Long,..))",
            returning = "result")
    public void logUpdateCurrentAccount(Object result) {
        try {
            final AccountDto account = (AccountDto) result;
            auditProducer.sendAuditLogRequest(account, OperationType.UPDATE);

            log.info("Aspect 'update' completed successfully");
        } catch (Exception e) {
            log.error("Failed to update audit: ", e);
        }
    }
}

