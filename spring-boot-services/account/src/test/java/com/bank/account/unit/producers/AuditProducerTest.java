package com.bank.account.unit.producers;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.enums.OperationType;
import com.bank.account.producers.AuditProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditProducerTest {

    @Mock
    private KafkaTemplate<String, AuditDto> kafkaTemplateAudit;

    @Mock
    private KafkaTemplate<String, AccountDto> kafkaTemplateAccount;

    @InjectMocks
    private AuditProducer auditProducer;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, AccountDto>> accountRecordCaptor;

    private static final String AUDIT_TOPIC = "audit.logs";
    private static final String EXTERNAL_AUDIT_TOPIC = "external.audit.logs";

    @BeforeEach
    void setUp() {
        kafkaTemplateAudit = mock(KafkaTemplate.class);
        kafkaTemplateAccount = mock(KafkaTemplate.class);
        auditProducer = new AuditProducer(kafkaTemplateAudit, kafkaTemplateAccount);

        ReflectionTestUtils.setField(auditProducer, "auditLogTopic", AUDIT_TOPIC);
        ReflectionTestUtils.setField(auditProducer, "externalAuditLogTopic", EXTERNAL_AUDIT_TOPIC);
    }

    @Test
    void sendAuditLogEvent_Success() {
        AuditDto auditDto = new AuditDto();
        auditDto.setId(1L);

        CompletableFuture future =
                CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplateAudit.send(EXTERNAL_AUDIT_TOPIC, auditDto)).thenReturn(future);

        auditProducer.sendAuditLogEvent(auditDto);

        verify(kafkaTemplateAudit, times(1)).send(eq(EXTERNAL_AUDIT_TOPIC), eq(auditDto));
    }

    @Test
    void sendAuditLogEvent_Exception() {
        AuditDto auditDto = new AuditDto();
        doThrow(new RuntimeException("Kafka error")).when(kafkaTemplateAudit).send(anyString(), any());

        try {
            auditProducer.sendAuditLogEvent(auditDto);
        } catch (RuntimeException e) {
            assertEquals("Failed to send audit log", e.getMessage());
        }

        verify(kafkaTemplateAudit, times(1)).send(anyString(), any());
    }

    @Test
    void sendAuditLogRequest_Success() {
        AccountDto accountDto = new AccountDto();
        OperationType operationType = OperationType.CREATE;

        when(kafkaTemplateAccount.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        auditProducer.sendAuditLogRequest(accountDto, operationType);

        verify(kafkaTemplateAccount).send(accountRecordCaptor.capture());
        ProducerRecord<String, AccountDto> record = accountRecordCaptor.getValue();

        assertAll(
                () -> assertEquals(AUDIT_TOPIC, record.topic()),
                () -> assertEquals(accountDto, record.value()),
                () -> {
                    Headers headers = record.headers();
                    Header header = headers.headers("operationType").iterator().next();
                    assertEquals(operationType.name(), new String(header.value(), StandardCharsets.UTF_8));
                }
        );
    }

    @Test
    void sendAuditLogRequest_ShouldNotThrowExceptionOnFailure() {
        AccountDto accountDto = new AccountDto();
        OperationType operationType = OperationType.UPDATE;

        assertDoesNotThrow(() -> auditProducer.sendAuditLogRequest(accountDto, operationType));
    }
}
