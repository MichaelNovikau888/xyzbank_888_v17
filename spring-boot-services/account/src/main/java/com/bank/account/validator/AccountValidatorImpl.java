package com.bank.account.validator;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountValidatorImpl implements AccountValidator {
    private static final String ACCOUNT_NOT_FOUND_MESSAGE = "Account not found with id: ";
    private static final String ACCOUNT_NUMBER_EXIST_MESSAGE = "Account number already in use by another account";
    private static final String BANK_DETAILS_ID_EXIST_MESSAGE = "Bank details already in use by another account";

    private final AccountRepository accountRepository;

    @Override
    public void validate(AccountDto accountDto) {
        if (accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber())) {
            log.error("Account number already exists: {}", accountDto.getAccountNumber());
            throw new IllegalArgumentException("Account number already exists");
        }

        if (accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId())) {
            log.error("Bank details id already exists: {}", accountDto.getBankDetailsId());
            throw new IllegalArgumentException("Bank details id already exists");
        }
    }

    @Override
    public void validateForUpdate(Long id, AccountDto accountDto) {
        accountRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE + id));

        if (accountRepository.existsAccountByAccountNumber(accountDto.getAccountNumber())) {
            final Account existingAccount = accountRepository.findAccountByAccountNumber(accountDto.getAccountNumber());
            if (!existingAccount.getId().equals(id)) {
                log.error(ACCOUNT_NUMBER_EXIST_MESSAGE);
                throw new IllegalArgumentException(ACCOUNT_NUMBER_EXIST_MESSAGE);
            }
        }

        if (accountRepository.existsAccountByBankDetailsId(accountDto.getBankDetailsId())) {
            final Account existingAccount = accountRepository.findAccountByBankDetailsId(accountDto.getBankDetailsId());
            if (!existingAccount.getId().equals(id)) {
                log.error(BANK_DETAILS_ID_EXIST_MESSAGE);
                throw new IllegalArgumentException(BANK_DETAILS_ID_EXIST_MESSAGE);
            }
        }
    }
}
