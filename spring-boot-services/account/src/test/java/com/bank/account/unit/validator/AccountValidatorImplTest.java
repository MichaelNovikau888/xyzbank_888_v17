package com.bank.account.unit.validator;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.repository.AccountRepository;
import com.bank.account.validator.AccountValidatorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountValidatorImplTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountValidatorImpl accountValidator;

    @Test
    void validate_ShouldThrowException_WhenAccountNumberExists() {
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountValidator.validate(accountDto));

        assertEquals("Account number already exists", exception.getMessage());
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository, never()).existsAccountByBankDetailsId(anyLong());
    }

    @Test
    void validate_ShouldThrowException_WhenBankDetailsIdExists() {
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(false);
        when(accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId()))
                .thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountValidator.validate(accountDto));

        assertEquals("Bank details id already exists", exception.getMessage());
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository).existsAccountByBankDetailsId(accountDto.getBankDetailsId());
    }

    @Test
    void validate_ShouldNotThrowException_Success() {
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(false);
        when(accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accountValidator.validate(accountDto));
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository).existsAccountByBankDetailsId(accountDto.getBankDetailsId());
    }

    @Test
    void validateForUpdate_ShouldThrowException_WhenAccountNumberUsedByOtherAccount() {
        Long id = 1L;
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        Account existingAccount = new Account();
        existingAccount.setId(2L);

        when(accountRepository.findById(id)).thenReturn(Optional.of(new Account()));
        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(true);
        when(accountRepository.findAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(existingAccount);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountValidator.validateForUpdate(id, accountDto));

        assertEquals("Account number already in use by another account", exception.getMessage());
        verify(accountRepository).findById(id);
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository).findAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository, never()).existsAccountByBankDetailsId(anyLong());
    }

    @Test
    void validateForUpdate_ShouldThrowException_WhenBankDetailsUsedByOtherAccount() {
        Long id = 1L;
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        Account existingAccount = new Account();
        existingAccount.setId(2L);

        when(accountRepository.findById(id)).thenReturn(Optional.of(new Account()));
        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(false);
        when(accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId()))
                .thenReturn(true);
        when(accountRepository.findAccountByBankDetailsId(accountDto.getBankDetailsId()))
                .thenReturn(existingAccount);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountValidator.validateForUpdate(id, accountDto));

        assertEquals("Bank details already in use by another account", exception.getMessage());
        verify(accountRepository).findById(id);
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository).existsAccountByBankDetailsId(accountDto.getBankDetailsId());
        verify(accountRepository).findAccountByBankDetailsId(accountDto.getBankDetailsId());
    }

    @Test
    void validateForUpdate_ShouldNotThrowException_Success() {
        Long id = 1L;
        AccountDto accountDto = new AccountDto();
        accountDto.setAccountNumber("123456");
        accountDto.setBankDetailsId(1L);

        when(accountRepository.findById(id)).thenReturn(Optional.of(new Account()));
        when(accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber()))
                .thenReturn(false);
        when(accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId()))
                .thenReturn(false);

        assertDoesNotThrow(() -> accountValidator.validateForUpdate(id, accountDto));
        verify(accountRepository).findById(id);
        verify(accountRepository).existsAccountByAccountNumber(accountDto.getAccountNumber());
        verify(accountRepository).existsAccountByBankDetailsId(accountDto.getBankDetailsId());
    }
}
