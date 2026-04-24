package com.bank.account.repository;

import com.bank.account.entity.Account;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account findAccountByAccountNumber(@NotNull String accountNumber);

    Account findAccountByBankDetailsId(@NotNull Long bankDetailsId);

    Account findAccountById(@NotNull Long id);
    
    boolean existsAccountByAccountNumber(@NotNull String accountNumber);

    boolean existsAccountByBankDetailsId(@NotNull Long bankDetailsId);
}
