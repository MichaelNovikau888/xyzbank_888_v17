package com.bank.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** Все неотправленные события, не превысившие порог попыток */
    @Query("SELECT e FROM OutboxEvent e WHERE e.sentAt IS NULL AND e.attempts < 5 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();
}
