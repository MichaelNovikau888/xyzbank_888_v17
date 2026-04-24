package com.bank.account.unit.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceGetTest {

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
    void getAllAccounts_Success() {
        when(accountRepositoryMock.findAll()).thenReturn(Collections.singletonList(accountSaved));
        when(accountMapperMock.toDto(any(Account.class))).thenReturn(accountDtoSaved);

        List<AccountDto> result = accountService.getAllAccounts();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(accountDtoSaved.getId(), result.get(0).getId());
    }

    @Test
    void getAllAccounts_EmptyList() {
        when(accountRepositoryMock.findAll()).thenReturn(Collections.emptyList());

        List<AccountDto> result = accountService.getAllAccounts();

        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
