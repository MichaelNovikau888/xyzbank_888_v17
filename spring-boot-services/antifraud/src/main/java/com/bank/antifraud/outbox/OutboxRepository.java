package com.bank.antifraud.outbox;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends CrudRepository<OutboxEvent, Long> {

    @Query("SELECT * FROM outbox_events WHERE sent_at IS NULL AND attempts < 5 ORDER BY created_at ASC")
    List<OutboxEvent> findPendingEvents();
}
