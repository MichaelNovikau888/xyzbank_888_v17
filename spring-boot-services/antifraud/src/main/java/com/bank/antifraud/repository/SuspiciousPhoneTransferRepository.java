package com.bank.antifraud.repository;

import com.bank.antifraud.model.SuspiciousPhoneTransfer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuspiciousPhoneTransferRepository extends CrudRepository<SuspiciousPhoneTransfer, Long> {

    @Query("SELECT * FROM suspicious_phone_transfers WHERE phone_transfer_id = :phoneTransferId LIMIT 1")
    Optional<SuspiciousPhoneTransfer> findByPhoneTransferId(long phoneTransferId);

    @Query("SELECT COUNT(*) FROM suspicious_phone_transfers WHERE is_blocked = true")
    long countByBlockedTrue();
}
