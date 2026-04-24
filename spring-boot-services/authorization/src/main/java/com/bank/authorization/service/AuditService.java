package com.bank.authorization.service;

import com.bank.authorization.dto.AuditDto;
import com.bank.authorization.dto.UserDto;

public interface AuditService {
    void save(AuditDto auditDto);
    void logUserCreation(UserDto userDto);
    void logUserUpdate(Long userId, UserDto userDto);
}
