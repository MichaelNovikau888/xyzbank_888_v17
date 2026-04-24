package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.repository.AccountRepository;
import com.bank.account.service.AccountServiceImpl;
import com.bank.account.utils.TestUtils;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceUpdateTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountValidator accountValidator;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account accountSaved;
    private AccountDto accountDtoSaved;
    private AccountDto accountDtoUpdated;
    private AccountDto accountDtoUpdatedSaved;
    private Account accountSavedUpdate;

    @BeforeEach
    void setUp() {
        accountSaved = TestUtils.createAccount(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        accountDtoSaved = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        accountDtoUpdated = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("-17800.00"), true, 154L, 154L);

        accountDtoUpdatedSaved = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("-17800.00"), true, 154L, 154L);

        accountSavedUpdate = TestUtils.createAccount(1L, 156L, "7654321",
                new BigDecimal("-17800.00"), true, 154L, 154L);
    }

    @Test
    void updateCurrentAccount_Success() {
        AccountDto updatedDto = new AccountDto();
        updatedDto.setAccountNumber("8765432");
        updatedDto.setMoney(new BigDecimal("500.00"));
        updatedDto.setBankDetailsId(155L);
        updatedDto.setProfileId(155L);
        updatedDto.setPassportId(157L);

        when(accountRepository.findAccountById(anyLong())).thenReturn(accountSaved);
        doNothing().when(accountValidator).validateForUpdate(anyLong(), any(AccountDto.class));
        when(accountRepository.save(any(Account.class))).thenReturn(accountSavedUpdate);
        when(accountMapper.toDto(any(Account.class))).thenReturn(accountDtoUpdatedSaved);

        AccountDto result = accountService.updateCurrentAccount(1L, updatedDto);

        assertNotNull(result);
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void updateCurrentAccount_AccountNotFound() {
        doThrow(new EntityNotFoundException("Account not found with id: 1"))
                .when(accountValidator).validateForUpdate(anyLong(), any(AccountDto.class));

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> accountService.updateCurrentAccount(1L, accountDtoUpdated)
        );

        assertEquals("Account not found with id: 1", exception.getMessage());
    }

    @Test
    void updateCurrentAccount_BankDetailsIdAlreadyExist() {
        doThrow(new IllegalArgumentException("Bank details already in use by another account"))
                .when(accountValidator).validateForUpdate(1L, accountDtoUpdated);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateCurrentAccount(1L, accountDtoUpdated)
        );

        assertEquals("Bank details already in use by another account", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateCurrentAccount_AccountNumberAlreadyExist() {
        doThrow(new IllegalArgumentException("Account number already in use by another account"))
                .when(accountValidator).validateForUpdate(1L, accountDtoUpdated);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.updateCurrentAccount(1L, accountDtoUpdated)
        );

        assertEquals("Account number already in use by another account", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }
}
