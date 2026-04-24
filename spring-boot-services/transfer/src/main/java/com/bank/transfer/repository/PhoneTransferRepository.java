package com.bank.transfer.repository;

import com.bank.transfer.entity.PhoneTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PhoneTransferRepository extends JpaRepository<PhoneTransfer, Long> {

    @Query("SELECT COUNT(t) > 0 FROM PhoneTransfer t " +
           "WHERE t.phoneNumber = :phoneNumber " +
           "AND t.accountDetailsId = :accountDetailsId " +
           "AND t.amount = :amount")
    boolean existsByPhoneNumberAndAccountDetailsIdAndAmount(
            @Param("phoneNumber") String phoneNumber,
            @Param("accountDetailsId") Long accountDetailsId,
            @Param("amount") BigDecimal amount);
}
