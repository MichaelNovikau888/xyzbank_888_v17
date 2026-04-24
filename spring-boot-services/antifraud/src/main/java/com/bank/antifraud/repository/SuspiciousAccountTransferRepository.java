package com.bank.antifraud.repository;

import com.bank.antifraud.model.SuspiciousAccountTransfer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuspiciousAccountTransferRepository extends CrudRepository<SuspiciousAccountTransfer, Long> {

    @Query("SELECT * FROM suspicious_account_transfers WHERE account_transfer_id = :accountTransferId LIMIT 1")
    Optional<SuspiciousAccountTransfer> findByAccountTransferId(long accountTransferId);

    @Query("SELECT COUNT(*) FROM suspicious_account_transfers WHERE is_blocked = true")
    long countByBlockedTrue();
}
