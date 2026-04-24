-- =============================================================================
-- import.sql — DDL и фикстуры для тест-профиля (H2 in-memory)
--
-- Причина: в тест-профиле quarkus.liquibase.enabled=false, поэтому Liquibase
-- не создаёт таблицу kafka_idempotent_offset.
-- Hibernate создаёт payment_reports и transfer_reports через
-- hibernate.generation=drop-and-create, но не умеет создавать DDL-объекты,
-- не привязанные к @Entity (kafka_idempotent_offset — вспомогательная таблица).
--
-- Этот файл выполняется Quarkus после Hibernate DDL при старте тестов.
-- H2 не поддерживает PARTITION BY и BIGSERIAL, поэтому используем
-- совместимый синтаксис.
-- =============================================================================

-- Таблица Exactly-Once: хранит обработанные Kafka offsets.
-- В тестах не используется напрямую (смоделировано через уникальные индексы
-- на payment_id / transfer_id), но наличие таблицы позволяет тестировать
-- checkPartitionHealth() и любой код, который к ней обращается.
CREATE TABLE IF NOT EXISTS kafka_idempotent_offset (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic         VARCHAR(200)  NOT NULL,
    partition_num INTEGER       NOT NULL,
    kafka_offset  BIGINT        NOT NULL,
    processed_at  TIMESTAMP     NOT NULL,
    CONSTRAINT uq_kafka_offset UNIQUE (topic, partition_num, kafka_offset)
);