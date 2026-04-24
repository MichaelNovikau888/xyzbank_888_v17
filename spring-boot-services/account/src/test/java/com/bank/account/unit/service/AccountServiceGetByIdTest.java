package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceGetByIdTest {

    @Mock
    private AccountRepository accountRepositoryMock;

    @Mock
    private AccountMapper accountMapperMock;

    @InjectMocks
    private AccountServiceImpl accountService;

    private Account accountSaved;
    private AccountDto accountDtoSaved;

    @BeforeEach
    void setUp() {
        accountSaved = TestUtils.createAccount(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);

        accountDtoSaved = TestUtils.createAccountDto(1L, 156L, "7654321",
                new BigDecimal("300.00"), false, 154L, 154L);
    }

    @Test
    void getAccountById_Success() {
        when(accountRepositoryMock.findAccountById(anyLong())).thenReturn(accountSaved);
        when(accountMapperMock.toDto(any(Account.class))).thenReturn(accountDtoSaved);

        AccountDto result = accountService.getAccountById(1L);

        assertNotNull(result);
        assertEquals(accountDtoSaved.getId(), result.getId());
        assertEquals(accountDtoSaved.getAccountNumber(), result.getAccountNumber());
    }

    @Test
    void getAccountById_AccountNotFound() {
        when(accountRepositoryMock.findAccountById(anyLong())).thenReturn(null);
        assertThrows(EntityNotFoundException.class, () -> accountService.getAccountById(1L));
    }
}
