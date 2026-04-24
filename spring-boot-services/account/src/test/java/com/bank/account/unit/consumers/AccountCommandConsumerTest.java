package com.bank.account.unit.consumers;

import com.bank.account.config.KafkaTopicsConfig;
import com.bank.account.consumers.AccountCommandConsumer;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AccountCommandConsumerTest {

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
    private AccountCommandConsumer accountCommandConsumer;

    private final String validJwtToken = "Bearer valid.token.here";
    private final String externalAccountCreate = "external-account-create";
    private final String externalAccountUpdate = "external-account-update";
    private final String externalAccountDelete = "external-account-delete";
    private final String errorTopic = "error-topic";

    @Test
    void handleCreateAccount_Success() {
        AccountDto inputAccount = new AccountDto();
        AccountDto createdAccount = new AccountDto();
        createdAccount.setId(1L);

        when(kafkaTopicsConfig.getExternalAccountCreate()).thenReturn(externalAccountCreate);
        when(accountService.createNewAccount(inputAccount)).thenReturn(createdAccount);

        Message<AccountDto> message = MessageBuilder.withPayload(inputAccount)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountCommandConsumer.handleCreateAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).createNewAccount(inputAccount);
        verify(accountProducer).sendExternalEvent(externalAccountCreate, createdAccount);
        verifyNoInteractions(kafkaErrorSender);
    }

    @Test
    void handleCreateAccount_ValidationFailed() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountCommandConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountCommandConsumer, errorTopic);

        AccountDto inputAccount = new AccountDto();
        String invalidToken = "Bearer invalid.token";
        RuntimeException validationException = new RuntimeException("Token validation failed");

        doThrow(validationException).when(tokenValidationService).validateJwtOrThrow(invalidToken);

        Message<AccountDto> message = MessageBuilder.withPayload(inputAccount)
                .setHeader("Authorization", invalidToken)
                .build();

        accountCommandConsumer.handleCreateAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(invalidToken);
        verifyNoInteractions(accountService);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(validationException, errorTopic);
    }

    @Test
    void handleUpdateAccount_Success() {
        AccountDto inputAccount = new AccountDto();
        inputAccount.setId(1L);
        AccountDto updatedAccount = new AccountDto();
        updatedAccount.setId(1L);

        when(kafkaTopicsConfig.getExternalAccountUpdate()).thenReturn(externalAccountUpdate);
        when(accountService.updateCurrentAccount(inputAccount.getId(), inputAccount)).thenReturn(updatedAccount);

        Message<AccountDto> message = MessageBuilder.withPayload(inputAccount)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountCommandConsumer.handleUpdateAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).updateCurrentAccount(inputAccount.getId(), inputAccount);
        verify(accountProducer).sendExternalEvent(externalAccountUpdate, updatedAccount);
        verifyNoInteractions(kafkaErrorSender);
    }

    @Test
    void handleUpdateAccount_AccountNotFound() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountCommandConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountCommandConsumer, errorTopic);

        AccountDto inputAccount = new AccountDto();
        inputAccount.setId(999L);
        RuntimeException notFoundException = new RuntimeException("Account not found");

        when(accountService.updateCurrentAccount(inputAccount.getId(), inputAccount)).thenThrow(notFoundException);

        Message<AccountDto> message = MessageBuilder.withPayload(inputAccount)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountCommandConsumer.handleUpdateAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).updateCurrentAccount(inputAccount.getId(), inputAccount);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(notFoundException, errorTopic);
    }

    @Test
    void handleDeleteAccount_Success() {
        when(kafkaTopicsConfig.getExternalAccountDelete()).thenReturn(externalAccountDelete);
        Long accountId = 1L;

        Message<Long> message = MessageBuilder.withPayload(accountId)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountCommandConsumer.handleDeleteAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).deleteAccount(accountId);
        verify(accountProducer).sendTextMessage(externalAccountDelete,
                String.format("Account with id: %s successfully deleted", accountId));
        verifyNoInteractions(kafkaErrorSender);
    }

    @Test
    void handleDeleteAccount_Failure() throws NoSuchFieldException, IllegalAccessException {
        Field topicErrorField = AccountCommandConsumer.class.getDeclaredField("topicError");
        topicErrorField.setAccessible(true);
        topicErrorField.set(accountCommandConsumer, errorTopic);

        Long accountId = 999L;
        RuntimeException deleteException = new RuntimeException("Delete failed");

        doThrow(deleteException).when(accountService).deleteAccount(accountId);

        Message<Long> message = MessageBuilder.withPayload(accountId)
                .setHeader("Authorization", validJwtToken)
                .build();

        accountCommandConsumer.handleDeleteAccount(message.getPayload(), message.getHeaders().get("Authorization", String.class));

        verify(tokenValidationService).validateJwtOrThrow(validJwtToken);
        verify(accountService).deleteAccount(accountId);
        verifyNoInteractions(accountProducer);
        verify(kafkaErrorSender).sendError(deleteException, errorTopic);
    }
}
