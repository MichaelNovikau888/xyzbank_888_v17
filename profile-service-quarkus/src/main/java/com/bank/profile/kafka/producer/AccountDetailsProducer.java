package com.bank.profile.kafka.producer;

import com.bank.profile.dto.AccountDetailsDto;
import com.bank.profile.util.KafkaTopic;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AccountDetailsProducer {

    private static final Logger log = Logger.getLogger(AccountDetailsProducer.class);

    @Inject
    @Channel("account-details-out")
    Emitter<AccountDetailsDto> accountEmitter;

    @Inject
    KafkaTopic topicsConfig;

    public void sendCreate(AccountDetailsDto dto) { send(topicsConfig.getTopicAccountDetailsCreate(), dto); }
    public void sendUpdate(AccountDetailsDto dto) { send(topicsConfig.getTopicAccountDetailsUpdate(), dto); }
    public void sendGetResponse(AccountDetailsDto dto) { send(topicsConfig.getTopicAccountDetailsGetResponse(), dto); }

    public void sendDelete(Long id) {
        AccountDetailsDto dto = new AccountDetailsDto();
        dto.setId(id);
        send(topicsConfig.getTopicAccountDetailsDelete(), dto);
    }

    public void sendGet(Long id) {
        AccountDetailsDto dto = new AccountDetailsDto();
        dto.setId(id);
        send(topicsConfig.getTopicAccountDetailsGet(), dto);
    }

    private void send(String topic, AccountDetailsDto dto) {
        log.debugf("Sending account-details event to topic=%s", topic);
        accountEmitter.send(
                Message.of(dto).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withTopic(topic)
                                .build()
                )
        );
    }
}
