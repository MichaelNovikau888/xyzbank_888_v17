package com.bank.account.validator;

import com.bank.account.dto.AccountDto;

public interface AccountValidator {
    void validate(AccountDto accountDto);
    void validateForUpdate(Long id, AccountDto accountDto);
}
