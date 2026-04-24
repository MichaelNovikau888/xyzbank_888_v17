package com.bank.antifraud.kafkaProducer;

import com.bank.antifraud.dto.AuditDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendAuditLog(AuditDto auditDto) {
        sendToKafka("audit.logs", auditDto, null, null);
    }

    public void sendAuditLogRequest(Object result, String operationType) {
        ProducerRecord<String, Object> record = new ProducerRecord<>("audit.logs", null, result);
        record.headers().add(new RecordHeader("operationType", operationType.getBytes(StandardCharsets.UTF_8)));
        sendToKafka(record);
    }

    // package-private для тестирования
    void sendToKafka(String topic, Object payload, String key, String operationType) {
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
            if (operationType != null) {
                record.headers().add(new RecordHeader("operationType", operationType.getBytes(StandardCharsets.UTF_8)));
            }
            kafkaTemplate.send(record);
            log.info("Message sent to Kafka topic '{}' with operationType '{}'", topic, operationType);
        } catch (Exception e) {
            log.error("Failed to send message to Kafka topic '{}':", topic, e);
        }
    }

    // package-private для тестирования
    void sendToKafka(ProducerRecord<String, Object> record) {
        try {
            kafkaTemplate.send(record);
            log.info("Message sent to Kafka topic '{}'", record.topic());
        } catch (Exception e) {
            log.error("Failed to send message to Kafka topic '{}':", record.topic(), e);
        }
    }
}
