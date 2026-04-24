package com.bank.account.unit.service;

import com.bank.account.entity.Account;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountServiceImpl;
import com.bank.account.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceDeleteTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account accountSaved;

    @BeforeEach
    void setUp() {
        accountSaved = TestUtils.createAccount(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);
    }

        @Test
    void deleteAccount_Success() {
        when(accountRepository.findAccountById(anyLong())).thenReturn(accountSaved);

        accountService.deleteAccount(1L);

        verify(accountRepository, times(1)).findAccountById(1L);
        verify(accountRepository, times(1)).delete(any(Account.class));
    }

    @Test
    void deleteAccount_AccountNotFound() {
        when(accountRepository.findAccountById(anyLong())).thenReturn(null);

        assertThrows(EntityNotFoundException.class, () -> accountService.deleteAccount(1L));
        verify(accountRepository, times(1)).findAccountById(1L);
        verify(accountRepository, never()).delete(any(Account.class));
    }
}
