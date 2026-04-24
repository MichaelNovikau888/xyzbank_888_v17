package com.bank.account.producers;

import com.bank.account.config.KafkaTopicsConfig;
import com.bank.account.dto.AccountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class AccountProducer {

    @Value("${kafka.header}")
    private String headerAuth;

    private final KafkaTemplate<String, AccountDto> kafkaTemplateAccount;
    private final KafkaTopicsConfig kafkaTopicsConfig;
    private final KafkaTemplate<String, String> kafkaTemplateString;
    private final KafkaTemplate<String, List<AccountDto>> kafkaTemplateAccountsList;

    public void sendCreatedAccountEvent(String json, String jwtToken) {
        sendMessageToTopic(json, kafkaTopicsConfig.getAccountCreate(), jwtToken,
                "Created account event", "account creation");
    }

    public void sendUpdatedAccountEvent(String json, String jwtToken) {
        sendMessageToTopic(json, kafkaTopicsConfig.getAccountUpdate(), jwtToken,
                "Updated account event", "account update");
    }

    public void sendDeletedAccountEvent(String json, String jwtToken) {
        sendMessageToTopic(json, kafkaTopicsConfig.getAccountDelete(), jwtToken,
                "Deleted account event", "account delete");
    }

    public void sendGetAccountsEvent(String jwtToken) {
        sendMessageToTopic("", kafkaTopicsConfig.getAccountGet(), jwtToken,
                "Get accounts event", "accounts get");
    }

    public void sendGetOneAccountByIdEvent(String json, String jwtToken) {
        sendMessageToTopic(json, kafkaTopicsConfig.getAccountGetById(), jwtToken,
                "Get account by ID event", "account get by ID");
    }

    private void sendMessageToTopic(Object json, String topic, String jwtToken,
                                    String successMessage, String errorContext) {
        try {
            final Message<Object> message = MessageBuilder
                    .withPayload(json)
                    .setHeader(KafkaHeaders.TOPIC, topic)
                    .setHeader(headerAuth, jwtToken)
                    .build();
            kafkaTemplateAccount.send(message);
            log.info("{} successfully finished.", successMessage);
        } catch (Exception e) {
            log.error("Failed to send {} event: ", errorContext, e);
        }
    }

    public void sendExternalEvent(String topic, AccountDto payload) {
        try {
            kafkaTemplateAccount.send(topic, payload);
            log.info("External event. Successfully sent event to topic: {}", topic);
        } catch (Exception e) {
            log.error("External Event. Failed to send event to topic {}:", topic, e);
        }
    }

    public void sendTextMessage(String topic, String message) {
        try {
            kafkaTemplateString.send(topic, message);
            log.info("Text message sent to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send text message to topic {}:", topic, e);
        }
    }

    public void sendAccountList(String topic, List<AccountDto> accounts) {
        try {
            kafkaTemplateAccountsList.send(topic, accounts);
            log.info("Account list sent to topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to send account list to topic {}:", topic, e);
        }
    }
}
