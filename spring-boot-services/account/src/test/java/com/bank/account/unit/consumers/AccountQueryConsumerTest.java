package com.bank.account.unit.consumers;

import com.bank.account.config.KafkaTopicsConfig;
import com.bank.account.consumers.AccountQueryConsumer;
import com.bank.account.dto.AccountDto;
import com.bank.account.exception.KafkaErrorSender;
import com.bank.account.producers.AccountProducer;
import com.bank.account.security.TokenValidationService;
import com.bank.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AccountQueryConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AccountProducer accountProducer;

    @Mock
    private KafkaErrorSender kafkaErrorSender;

    @Mock
    private KafkaTopicsConfig kafkaTopicsConfig;

    @Mock
    private TokenValidationService tokenValidationService;

    @InjectMocks
    private AccountQueryConsumer accountQueryConsumer;

    private final String validJwtToken = "Bearer valid.token.here";
    private final String externalAccountGet = "external-account-get";
    private final String externalAccountGetById = "external-account-get-by-id";
    private final String errorTopic = "error-topic";

    @Test
    void handleGetAccounts_Success() {
        List<AccountDto> accounts = Collections.singletonList(new AccountDto());
        when(kafkaTopicsConfig.getExternalAccountGet()).thenReturn(externalAccountGet);
        when(accountService.getAllAccounts()).thenReturn(accounts);

        Message<String> message = MessageBuilder.withPayload("")
                .setHeader("Authorization", validJwtToken)
                .build();

        accountQueryConsumer.handleGetAccounts(message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).getAllAccounts();
        verify(accountProducer).sendAccountList(externalAccountGet, accounts);
        verifyNoInteractions(kafkaErrorSender);
    }

    @Test
    void handleGetAccounts_ValidationFailed() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountQueryConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountQueryConsumer, errorTopic);

        String invalidToken = "Bearer invalid.token";
        RuntimeException validationException = new RuntimeException("Token validation failed");

        doThrow(validationException).when(tokenValidationService).validateJwtOrThrow(invalidToken);

        Message<String> message = MessageBuilder.withPayload("")
                .setHeader("Authorization", invalidToken)
                .build();

        accountQueryConsumer.handleGetAccounts(message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(invalidToken);
        verifyNoInteractions(accountService);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(validationException, errorTopic);
    }

    @Test
    void handleGetAccounts_ServiceThrowsException() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountQueryConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountQueryConsumer, errorTopic);

        RuntimeException serviceException = new RuntimeException("Service error");
        when(accountService.getAllAccounts()).thenThrow(serviceException);

        Message<String> message = MessageBuilder.withPayload("")
                .setHeader("Authorization", validJwtToken)
                .build();

        accountQueryConsumer.handleGetAccounts(message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).getAllAccounts();
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(serviceException, errorTopic);
    }

    @Test
    void handleGetByIdAccount_Success() {
        Long accountId = 1L;
        AccountDto account = new AccountDto();
        account.setId(accountId);

        when(kafkaTopicsConfig.getExternalAccountGetById()).thenReturn(externalAccountGetById);
        when(accountService.getAccountById(accountId)).thenReturn(account);

        Message<Long> message = MessageBuilder.withPayload(accountId)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountQueryConsumer.handleGetByIdAccount(message.getPayload(),
                message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).getAccountById(accountId);
        verify(accountProducer).sendExternalEvent(externalAccountGetById, account);
        verifyNoInteractions(kafkaErrorSender);
    }

    @Test
    void handleGetByIdAccount_AccountNotFound() throws IllegalAccessException, NoSuchFieldException {
        Field topicErrorField = AccountQueryConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountQueryConsumer, errorTopic);

        Long accountId = 999L;
        RuntimeException notFoundException = new RuntimeException("Account not found");

        when(accountService.getAccountById(accountId)).thenThrow(notFoundException);

        Message<Long> message = MessageBuilder.withPayload(accountId)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountQueryConsumer.handleGetByIdAccount(message.getPayload(),
                message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).getAccountById(accountId);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(notFoundException, errorTopic);
    }

    @Test
    void handleGetByIdAccount_InvalidToken() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountQueryConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountQueryConsumer, errorTopic);

        Long accountId = 1L;
        String invalidToken = "Bearer invalid.token";
        RuntimeException validationException = new RuntimeException("Token validation failed");

        doThrow(validationException).when(tokenValidationService).validateJwtOrThrow(invalidToken);

        Message<Long> message = MessageBuilder.withPayload(accountId)
                .setHeader("Authorization", invalidToken)
                .build();

        accountQueryConsumer.handleGetByIdAccount(message.getPayload(),
                message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(invalidToken);
        verifyNoInteractions(accountService);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(validationException, errorTopic);
    }
}
