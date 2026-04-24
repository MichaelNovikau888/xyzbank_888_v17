package com.bank.antifraud.repository;

import com.bank.antifraud.model.SuspiciousCardTransfer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuspiciousCardTransferRepository extends CrudRepository<SuspiciousCardTransfer, Long> {

 /**
     * Idempotency check: был ли уже проанализирован перевод с данным card_transfer_id?
     * Используется в SuspiciousTransferConsumer перед каждым analyzeCardTransfer().
 */
    @Query("SELECT * FROM suspicious_card_transfer WHERE card_transfer_id = :cardTransferId LIMIT 1")
    Optional<SuspiciousCardTransfer> findByCardTransferId(long cardTransferId);

    @Query("SELECT COUNT(*) FROM suspicious_card_transfer WHERE is_blocked = true")
    long countByBlockedTrue();
}
