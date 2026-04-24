package com.bank.account.unit.service;

import com.bank.account.service.AccountServiceImpl;
import com.bank.account.utils.TestUtils;
import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.repository.AccountRepository;
import com.bank.account.validator.AccountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceCreateTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountValidator accountValidator;

    @InjectMocks
    private AccountServiceImpl accountService;

    private AccountDto accountDto;
    private Account account;
    private Account accountSaved;
    private AccountDto accountDtoSaved;

    @BeforeEach
    void setUp() {
        accountDto = TestUtils.createAccountDto(null,156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        account = TestUtils.createAccount(null,156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        accountSaved = TestUtils.createAccount(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        accountDtoSaved = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);
    }

    @Test
    void createNewAccount_Success() {
        doNothing().when(accountValidator).validate(any(AccountDto.class));
        when(accountMapper.toAccount(any(AccountDto.class))).thenReturn(account);
        when(accountRepository.save(any(Account.class))).thenReturn(accountSaved);
        when(accountMapper.toDto(any(Account.class))).thenReturn(accountDtoSaved);

        AccountDto result = accountService.createNewAccount(accountDto);

        assertNotNull(result);
        assertEquals(accountDtoSaved.getId(), result.getId());
        assertEquals(accountDtoSaved.getAccountNumber(), result.getAccountNumber());
        assertEquals(accountDtoSaved.isNegativeBalance(), result.isNegativeBalance());
        verify(accountRepository, times(1)).save(any(Account.class));
    }


    @Test
    void createNewAccount_AccountNumberAlreadyExists() {
        doThrow(new IllegalArgumentException("Account number already exists"))
                .when(accountValidator).validate(any(AccountDto.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createNewAccount(accountDto)
        );

        assertEquals("Account number already exists", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createNewAccount_BankDetailsIdAlreadyExists() {
        doThrow(new IllegalArgumentException("Bank details id already exists"))
                .when(accountValidator).validate(any(AccountDto.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createNewAccount(accountDto)
        );

        assertEquals("Bank details id already exists", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }


    @Test
    void createNewAccount_RuntimeException() {
        doNothing().when(accountValidator).validate(any(AccountDto.class));
        when(accountMapper.toAccount(any(AccountDto.class))).thenReturn(account);
        when(accountRepository.save(any(Account.class))).thenThrow(new RuntimeException("Database error"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> accountService.createNewAccount(accountDto)
        );

        assertTrue(exception.getMessage().contains("Database error"));
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}
