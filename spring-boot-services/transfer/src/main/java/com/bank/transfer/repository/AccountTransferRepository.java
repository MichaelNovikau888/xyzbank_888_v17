package com.bank.transfer.repository;

import com.bank.transfer.entity.AccountTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface AccountTransferRepository extends JpaRepository<AccountTransfer, Long> {

 /**
     * Idempotency check: был ли уже сохранён перевод с теми же реквизитами?
     * Комбинация accountNumber + accountDetailsId + amount образует составной
     * натуральный ключ для повторной доставки Kafka at-least-once.
 */
    @Query("SELECT COUNT(t) > 0 FROM AccountTransfer t " +
           "WHERE t.accountNumber = :accountNumber " +
           "AND t.accountDetailsId = :accountDetailsId " +
           "AND t.amount = :amount")
    boolean existsByAccountNumberAndAccountDetailsIdAndAmount(
            @Param("accountNumber") String accountNumber,
            @Param("accountDetailsId") Long accountDetailsId,
            @Param("amount") BigDecimal amount);
}
