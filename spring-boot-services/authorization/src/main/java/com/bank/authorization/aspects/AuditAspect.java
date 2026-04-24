package com.bank.authorization.aspects;

import com.bank.authorization.dto.UserDto;
import com.bank.authorization.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Component
@Aspect
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @AfterReturning(pointcut = "execution(* com.bank.authorization.service.UserServiceImpl.save(..))",
            returning = "result")
    public void logSave(Object result) {
        if (result != null && result instanceof UserDto userDto) {
            auditService.logUserCreation(userDto);
        }
    }

    @AfterReturning(pointcut = "execution(* com.bank.authorization.service.UserServiceImpl.updateUser(..))",
            returning = "result")
    public void logUpdate(Object result) {
        if (result != null && result instanceof UserDto userDto) {
            auditService.logUserUpdate(userDto.getId(), userDto);
        }
    }
}
