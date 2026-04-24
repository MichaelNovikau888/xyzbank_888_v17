package com.bank.antifraud.outbox;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Transactional Outbox для antifraud-service.
 * Использует Spring Data JDBC (как весь сервис — без Hibernate).
 */
@Table("outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    private Long id;

    @Column("topic")
    private String topic;

    @Column("partition_key")
    private String partitionKey;

    @Column("event_type")
    private String eventType;

    @Column("payload")
    private String payload;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("sent_at")
    private LocalDateTime sentAt;

    @Column("attempts")
    private int attempts;
}
