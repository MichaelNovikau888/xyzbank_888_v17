package com.bank.account.service;

import com.bank.account.dto.AccountDto;

import java.util.List;

public interface AccountService {
    AccountDto createNewAccount(AccountDto accountDto);

    AccountDto updateCurrentAccount(Long id, AccountDto accountDtoUpdated);

    void deleteAccount(Long id);

    AccountDto getAccountById(Long id);

    List<AccountDto> getAllAccounts();
}
