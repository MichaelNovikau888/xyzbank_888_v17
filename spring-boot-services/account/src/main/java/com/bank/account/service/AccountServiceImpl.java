package com.bank.account.service;

import com.bank.account.dto.AccountDto;
import com.bank.account.entity.Account;
import com.bank.account.exception.custom_exceptions.EntityNotFoundException;
import com.bank.account.mapper.AccountMapper;
import com.bank.account.metrics.AccountMetrics;
import com.bank.account.outbox.AccountOutboxHelper;
import com.bank.account.repository.AccountRepository;
import com.bank.account.validator.AccountValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final String ACCOUNT_NOT_FOUND = "Account not found with id: ";

    private final AccountRepository  accountRepository;
    private final AccountMapper      accountMapper;
    private final AccountValidator   accountValidator;
    private final AccountOutboxHelper outboxHelper;
    private final AccountMetrics     metrics;

    @Override
    @Transactional
    public AccountDto createNewAccount(AccountDto accountDto) {
        accountValidator.validate(accountDto);
        accountDto.setNegativeBalance(isNegativeBalance(accountDto));
        final Account saved = accountRepository.save(accountMapper.toAccount(accountDto));
        final AccountDto savedDto = accountMapper.toDto(saved);

        outboxHelper.enqueue("external.account.create", String.valueOf(saved.getId()),
                "AccountCreated", savedDto);
        metrics.getAccountsCreated().increment();
        log.info("Account created: accountNumber={}", saved.getAccountNumber());
        return savedDto;
    }

    @Override
    @Transactional
    public AccountDto updateCurrentAccount(Long id, AccountDto dto) {
        accountValidator.validateForUpdate(id, dto);
        final Account account = accountRepository.findAccountById(id);
        account.setAccountNumber(dto.getAccountNumber());
        account.setMoney(dto.getMoney());
        account.setNegativeBalance(isNegativeBalance(dto));
        account.setPassportId(dto.getPassportId());
        account.setBankDetailsId(dto.getBankDetailsId());
        account.setProfileId(dto.getProfileId());
        final Account saved = accountRepository.save(account);
        accountRepository.flush();
        final AccountDto savedDto = accountMapper.toDto(saved);

        outboxHelper.enqueue("external.account.update", String.valueOf(id), "AccountUpdated", savedDto);
        metrics.getAccountsUpdated().increment();
        log.info("Account updated: id={}", id);
        return savedDto;
    }

    @Override
    @Transactional
    public void deleteAccount(Long id) {
        final Account account = accountRepository.findAccountById(id);
        if (account == null) throw new EntityNotFoundException(ACCOUNT_NOT_FOUND + id);
        accountRepository.delete(account);
        outboxHelper.enqueue("external.account.delete", String.valueOf(id), "AccountDeleted",
                Map.of("id", id));
        metrics.getAccountsDeleted().increment();
        log.info("Account deleted: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long id) {
        final Account result = accountRepository.findAccountById(id);
        if (result == null) throw new EntityNotFoundException(ACCOUNT_NOT_FOUND + id);
        return accountMapper.toDto(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream().map(accountMapper::toDto).collect(Collectors.toList());
    }

    private boolean isNegativeBalance(AccountDto dto) {
        return dto.getMoney().compareTo(BigDecimal.ZERO) < 0;
    }
}
