package com.bank.transfer.repository;

import com.bank.transfer.entity.CardTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CardTransferRepository extends JpaRepository<CardTransfer, Long> {

    @Query("SELECT COUNT(t) > 0 FROM CardTransfer t " +
           "WHERE t.cardNumber = :cardNumber " +
           "AND t.accountDetailsId = :accountDetailsId " +
           "AND t.amount = :amount")
    boolean existsByCardNumberAndAccountDetailsIdAndAmount(
            @Param("cardNumber") String cardNumber,
            @Param("accountDetailsId") Long accountDetailsId,
            @Param("amount") BigDecimal amount);
}
