package com.bank.antifraud.kafkaProducer;

import com.bank.antifraud.dto.AuditDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.bank.antifraud.util.AuditUtil.CREATE_OPERATOR;
import static com.bank.antifraud.util.AuditUtil.TEST_TOPIC;
import static com.bank.antifraud.util.AuditUtil.UPDATE_OPERATOR;

@ExtendWith(MockitoExtension.class)
class AuditProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AuditProducer auditProducer;

    @Test
    void sendAuditLog_shouldSendMessageToKafka() {
        // Arrange
        AuditDto auditDto = new AuditDto();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        auditProducer.sendAuditLog(auditDto);

        // Assert
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, Object> sentRecord = recordCaptor.getValue();
        assertEquals("audit-topic", sentRecord.topic());
        assertEquals(auditDto, sentRecord.value());
        assertNull(sentRecord.key());
    }

    @Test
    void sendAuditLogRequest_shouldSendMessageWithOperationTypeHeader() {
        // Arrange
        Object payload = new AuditDto();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        auditProducer.sendAuditLogRequest(payload, CREATE_OPERATOR.getType());

        // Assert
        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, Object> sentRecord = recordCaptor.getValue();
        assertEquals("audit.logs", sentRecord.topic());
        assertEquals(payload, sentRecord.value());
        assertNull(sentRecord.key());

        RecordHeader header = (RecordHeader) sentRecord.headers().headers("operationType").iterator().next();
        assertEquals(CREATE_OPERATOR.getType(), new String(header.value(), StandardCharsets.UTF_8));
    }

    @Test
    void sendToKafka_shouldLogSuccess() {
        // Arrange
        Object payload = new AuditDto();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        auditProducer.sendToKafka(TEST_TOPIC.getType(), payload, null, UPDATE_OPERATOR.getType());

        // Assert
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    void sendToKafka_shouldLogErrorWhenSendingFails() {
        // Arrange
        Object payload = new AuditDto();
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        auditProducer.sendToKafka(TEST_TOPIC.getType(), payload, null, UPDATE_OPERATOR.getType());

        // Assert
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    void sendToKafkaWithRecord_shouldSendRecordToKafka() {
        // Arrange
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", "key", "value");
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        auditProducer.sendToKafka(record);

        // Assert
        verify(kafkaTemplate).send(record);
    }
}
