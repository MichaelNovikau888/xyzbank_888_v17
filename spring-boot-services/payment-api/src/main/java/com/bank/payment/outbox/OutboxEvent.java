package com.bank.payment.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Transactional Outbox — таблица исходящих событий.
 /
 * Запись создаётся в той же DB-транзакции, что и доменный объект (Payment).
 * OutboxRelayScheduler читает неотправленные события и публикует их в Kafka.
 * Это гарантирует: либо оба изменения применяются, либо ни одно.
 */
@Entity
@Table(name = "outbox_events", schema = "fastpay")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Имя Kafka-топика, в который нужно отправить событие */
    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    /** Partition key (например, payment_id) для гарантии порядка */
    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    /** Тип события: PaymentCreated, PaymentStatusChanged, … */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** JSON-сериализованный payload события */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Время создания записи */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Время успешной отправки в Kafka; null — ещё не отправлено */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** Количество попыток отправки (для dead-letter логики) */
    @Column(name = "attempts", nullable = false)
    private int attempts;
}
