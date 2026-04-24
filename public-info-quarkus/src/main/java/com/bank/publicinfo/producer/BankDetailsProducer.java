package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер BankDetails-событий (create / update / delete).
 */
 /**
 * Топик: public-info.bank.events
 * Канал: bank-events-out → задаётся в application.properties.
 */
@ApplicationScoped
public class BankDetailsProducer {

    private static final Logger LOG = Logger.getLogger(BankDetailsProducer.class);
    private static final String TOPIC = "public-info.bank.events";

    @Inject
    @Channel("bank-events-out")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    public void sendCreated(BankDetailsDto dto) { send("CREATED", dto); }
    public void sendUpdated(BankDetailsDto dto) { send("UPDATED", dto); }

    public void sendDeleted(Long id) {
        try {
            String payload = "{\"event\":\"DELETED\",\"id\":" + id + "}";
            emitter.send(Message.of(payload).addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder().withTopic(TOPIC).build()
            ));
            LOG.infof("BankDetails DELETED event sent: id=%d", id);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send BankDetails DELETED event for id=%d", id);
        }
    }

    private void send(String eventType, BankDetailsDto dto) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            String envelope = "{\"event\":\"" + eventType + "\",\"data\":" + payload + "}";
            emitter.send(Message.of(envelope).addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder().withTopic(TOPIC).build()
            ));
            LOG.infof("BankDetails %s event sent: id=%d", eventType, dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send BankDetails %s event", eventType);
        }
    }
}
