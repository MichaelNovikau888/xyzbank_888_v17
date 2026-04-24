package com.bank.account.unit.producers;

import com.bank.account.config.KafkaTopicsConfig;
import com.bank.account.dto.AccountDto;
import com.bank.account.producers.AccountProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountProducerTest {

    @Mock
    private KafkaTemplate<String, AccountDto> kafkaTemplateAccount;
    @Mock
    private KafkaTopicsConfig kafkaTopicsConfig;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplateString;
    @Mock
    private KafkaTemplate<String, List<AccountDto>> kafkaTemplateAccountsList;

    @InjectMocks
    private AccountProducer accountProducer;

    private final String jwtToken = "Bearer token";
    private final String accountJson = "{\"id\":1}";
    private final String accountCreateTopic = "account.create";
    private final String accountUpdateTopic = "account.update";
    private final String accountDeleteTopic = "account.delete";
    private final String accountGetTopic = "account.get";
    private final String accountGetByIdTopic = "account.get.by.id";

    @BeforeEach
    void setUp() {
        accountProducer = new AccountProducer(
                kafkaTemplateAccount,
                kafkaTopicsConfig,
                kafkaTemplateString,
                kafkaTemplateAccountsList
        );
        ReflectionTestUtils.setField(accountProducer, "headerAuth", "Authorization");
    }

    @Test
    void sendCreatedAccountEvent_Success() {
        when(kafkaTopicsConfig.getAccountCreate()).thenReturn(accountCreateTopic);

        accountProducer.sendCreatedAccountEvent(accountJson, jwtToken);

        verify(kafkaTemplateAccount).send(verifyMessage(accountJson, accountCreateTopic, jwtToken));
    }

    @Test
    void sendUpdatedAccountEvent_Success() {
        when(kafkaTopicsConfig.getAccountUpdate()).thenReturn(accountUpdateTopic);

        accountProducer.sendUpdatedAccountEvent(accountJson, jwtToken);

        verify(kafkaTemplateAccount).send(verifyMessage(accountJson, accountUpdateTopic, jwtToken));
    }

    @Test
    void sendDeletedAccountEvent_Success() {
        when(kafkaTopicsConfig.getAccountDelete()).thenReturn(accountDeleteTopic);

        accountProducer.sendDeletedAccountEvent(accountJson, jwtToken);

        verify(kafkaTemplateAccount).send(verifyMessage(accountJson, accountDeleteTopic, jwtToken));
    }

    @Test
    void sendGetAccountsEvent_Success() {
        when(kafkaTopicsConfig.getAccountGet()).thenReturn(accountGetTopic);

        accountProducer.sendGetAccountsEvent(jwtToken);

        verify(kafkaTemplateAccount).send(verifyMessage("", accountGetTopic, jwtToken));
    }

    @Test
    void sendGetOneAccountByIdEvent_Success() {
        when(kafkaTopicsConfig.getAccountGetById()).thenReturn(accountGetByIdTopic);

        accountProducer.sendGetOneAccountByIdEvent(accountJson, jwtToken);

        verify(kafkaTemplateAccount).send(verifyMessage(accountJson, accountGetByIdTopic, jwtToken));
    }

    @Test
    void sendExternalEvent_Success() {
        AccountDto accountDto = new AccountDto();
        accountDto.setId(1L);

        accountProducer.sendExternalEvent("external.topic", accountDto);

        verify(kafkaTemplateAccount).send("external.topic", accountDto);
    }

    @Test
    void sendTextMessage_Success() {
        accountProducer.sendTextMessage("text.topic", "Test message");

        verify(kafkaTemplateString).send("text.topic", "Test message");
    }

    @Test
    void sendAccountList_Success() {
        List<AccountDto> accounts = Collections.singletonList(new AccountDto());

        accountProducer.sendAccountList("accounts.topic", accounts);

        verify(kafkaTemplateAccountsList).send("accounts.topic", accounts);
    }

    @Test
    void sendMessageToTopic_ExceptionHandling() {
        when(kafkaTopicsConfig.getAccountCreate()).thenReturn(accountCreateTopic);
        doThrow(new RuntimeException("Kafka error")).when(kafkaTemplateAccount).send(any(Message.class));

        accountProducer.sendCreatedAccountEvent(accountJson, jwtToken);

        verify(kafkaTemplateAccount).send(any(Message.class));
    }

    private Message<Object> verifyMessage(Object payload, String topic, String jwtToken) {
        return argThat(message -> {
            assertEquals(payload, message.getPayload());
            assertEquals(topic, message.getHeaders().get(KafkaHeaders.TOPIC));
            assertEquals(jwtToken, message.getHeaders().get("Authorization"));
            return true;
        });
    }
}
