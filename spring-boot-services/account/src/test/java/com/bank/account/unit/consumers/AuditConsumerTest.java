package com.bank.account.unit.consumers;

import com.bank.account.consumers.AuditConsumer;
import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.exception.KafkaErrorSender;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.producers.AuditProducer;
import com.bank.account.service.AuditService;
import com.bank.account.utils.JsonUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditConsumerTest {

    @Mock
    private AuditService auditService;

    @Mock
    private AuditProducer auditProducer;

    @Mock
    private KafkaErrorSender kafkaErrorSender;

    @Mock
    private JsonUtils jsonUtils;

    @InjectMocks
    private AuditConsumer auditConsumer;

    @Test
    void handleAuditLogEvent_CreateOperation_Success() throws JsonProcessingException {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            AccountDto accountDto = new AccountDto();
            accountDto.setId(1L);
            AuditDto auditDto = new AuditDto();
            ConsumerRecord<String, AccountDto> record =
                    new ConsumerRecord<>("topic", 0, 0, "key", accountDto);

            mockedJsonUtils.when(() -> JsonUtils.extractHeader(record, "operationType"))
                    .thenReturn("CREATE");

            when(auditService.createAudit(accountDto)).thenReturn(auditDto);

            auditConsumer.handleAuditLogEvent(record);

            verify(auditService).createAudit(accountDto);
            verify(auditProducer).sendAuditLogEvent(auditDto);
            verifyNoInteractions(kafkaErrorSender);
        }
    }

    @Test
    void handleAuditLogEvent_UpdateOperation_Success() throws JsonProcessingException {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            AccountDto accountDto = new AccountDto();
            accountDto.setId(1L);
            AuditDto auditDto = new AuditDto();
            ConsumerRecord<String, AccountDto> record =
                    new ConsumerRecord<>("topic", 0, 0, "key", accountDto);

            mockedJsonUtils.when(() -> JsonUtils.extractHeader(record, "operationType"))
                    .thenReturn("UPDATE");

            when(auditService.updateAudit(accountDto)).thenReturn(auditDto);

            auditConsumer.handleAuditLogEvent(record);

            verify(auditService).updateAudit(accountDto);
            verify(auditProducer).sendAuditLogEvent(auditDto);
            verifyNoInteractions(kafkaErrorSender);
        }
    }

    @Test
    void handleAuditLogEvent_UnsupportedOperation() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            AccountDto accountDto = new AccountDto();
            accountDto.setId(1L);
            ConsumerRecord<String, AccountDto> record =
                    new ConsumerRecord<>("topic", 0, 0, "key", accountDto);

            mockedJsonUtils.when(() -> JsonUtils.extractHeader(record, "operationType"))
                    .thenReturn("DELETE");

            auditConsumer.handleAuditLogEvent(record);

            verifyNoInteractions(auditService);
            verifyNoInteractions(auditProducer);
            verify(kafkaErrorSender).sendError(any(IllegalArgumentException.class), eq(accountDto.toString()));
        }
    }

    @Test
    void handleAuditLogEvent_MissingOperationTypeHeader() {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            AccountDto accountDto = new AccountDto();
            accountDto.setId(1L);
            ConsumerRecord<String, AccountDto> record =
                    new ConsumerRecord<>("topic", 0, 0, "key", accountDto);

            mockedJsonUtils.when(() -> JsonUtils.extractHeader(record, "operationType"))
                    .thenReturn(null);

            auditConsumer.handleAuditLogEvent(record);

            verifyNoInteractions(auditService);
            verifyNoInteractions(auditProducer);
            verify(kafkaErrorSender).sendError(any(IllegalArgumentException.class), eq(accountDto.toString()));
        }
    }

    @Test
    void handleAuditLogEvent_ServiceThrowsException() throws JsonProcessingException {
        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            AccountDto accountDto = new AccountDto();
            accountDto.setId(1L);
            RuntimeException serviceException = new RuntimeException("Service error");
            ConsumerRecord<String, AccountDto> record =
                    new ConsumerRecord<>("topic", 0, 0, "key", accountDto);

            mockedJsonUtils.when(() -> JsonUtils.extractHeader(record, "operationType"))
                    .thenReturn("CREATE");

            when(auditService.createAudit(accountDto)).thenThrow(serviceException);

            auditConsumer.handleAuditLogEvent(record);

            verify(auditService).createAudit(accountDto);
            verifyNoInteractions(auditProducer);
            verify(kafkaErrorSender).sendError(serviceException, accountDto.toString());
        }
    }
}
