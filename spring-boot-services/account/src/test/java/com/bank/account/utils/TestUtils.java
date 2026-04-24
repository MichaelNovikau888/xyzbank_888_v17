package com.bank.account.utils;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.entity.Account;
import com.bank.account.entity.Audit;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class TestUtils {
    public static Account createAccount(Long id, Long passportId, String accountNumber, BigDecimal money,
                                        Boolean negativeBalance, Long bankDetailsId, Long profileId) {
        Account account = new Account();
        account.setId(id);
        account.setPassportId(passportId);
        account.setAccountNumber(accountNumber);
        account.setMoney(money);
        account.setNegativeBalance(negativeBalance);
        account.setBankDetailsId(bankDetailsId);
        account.setProfileId(profileId);
        return account;
    }

    public static AccountDto createAccountDto(Long id, Long passportId, String accountNumber, BigDecimal money,
                                              Boolean negativeBalance, Long bankDetailsId, Long profileId) {
        AccountDto accountDto = new AccountDto();
        accountDto.setId(id);
        accountDto.setPassportId(passportId);
        accountDto.setAccountNumber(accountNumber);
        accountDto.setMoney(money);
        accountDto.setNegativeBalance(negativeBalance);
        accountDto.setBankDetailsId(bankDetailsId);
        accountDto.setProfileId(profileId);
        return accountDto;
    }

    public static Audit createAudit(Long id, String entityType, String operationType, String createdBy, String modifiedBy,
                                    Timestamp createdAt, Timestamp modifiedAt, String entityJson, String newEntityJson) {
        Audit audit = new Audit();
        audit.setId(id);
        audit.setEntityType(entityType);
        audit.setOperationType(operationType);
        audit.setCreatedBy(createdBy);
        audit.setModifiedBy(modifiedBy);
        audit.setCreatedAt(createdAt);
        audit.setModifiedAt(modifiedAt);
        audit.setEntityJson(entityJson);
        audit.setNewEntityJson(newEntityJson);
        return audit;
    }

    public static AuditDto createAuditDto(Long id, String entityType, String operationType, String createdBy, String modifiedBy,
                                          Timestamp createdAt, Timestamp modifiedAt, String entityJson, String newEntityJson) {
        AuditDto auditDto = new AuditDto();
        auditDto.setId(id);
        auditDto.setEntityType(entityType);
        auditDto.setOperationType(operationType);
        auditDto.setCreatedBy(createdBy);
        auditDto.setModifiedBy(modifiedBy);
        auditDto.setCreatedAt(createdAt);
        auditDto.setModifiedAt(modifiedAt);
        auditDto.setEntityJson(entityJson);
        auditDto.setNewEntityJson(newEntityJson);
        return auditDto;
    }
}