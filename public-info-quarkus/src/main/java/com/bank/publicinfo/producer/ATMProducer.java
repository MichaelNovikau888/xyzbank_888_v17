package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.ATMDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер ATM-событий (create / update / delete).
 */
 /**
 * Топик: public-info.atm.events
 * Канал: atm-events-out → задаётся в application.properties.
 */
 /**
 * Используется в ATMServiceImpl после каждой мутирующей операции,
 * чтобы downstream-сервисы (history, audit) получили событие.
 */
@ApplicationScoped
public class ATMProducer {

    private static final Logger LOG = Logger.getLogger(ATMProducer.class);
    private static final String TOPIC = "public-info.atm.events";

    @Inject
    @Channel("atm-events-out")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

 /**
     * Отправляет событие о создании ATM.
 */
    public void sendCreated(ATMDto dto) {
        send("CREATED", dto);
    }

 /**
     * Отправляет событие об обновлении ATM.
 */
    public void sendUpdated(ATMDto dto) {
        send("UPDATED", dto);
    }

 /**
     * Отправляет событие об удалении ATM (только id).
 */
    public void sendDeleted(Long id) {
        try {
            String payload = "{\"event\":\"DELETED\",\"id\":" + id + "}";
            emitter.send(
                Message.of(payload).addMetadata(
                    OutgoingKafkaRecordMetadata.<String>builder()
                        .withTopic(TOPIC)
                        .build()
                )
            );
            LOG.infof("ATM DELETED event sent: id=%d", id);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send ATM DELETED event for id=%d", id);
        }
    }

    private void send(String eventType, ATMDto dto) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            String envelope = "{\"event\":\"" + eventType + "\",\"data\":" + payload + "}";
            emitter.send(
                Message.of(envelope).addMetadata(
                    OutgoingKafkaRecordMetadata.<String>builder()
                        .withTopic(TOPIC)
                        .build()
                )
            );
            LOG.infof("ATM %s event sent: id=%d", eventType, dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send ATM %s event for id=%d", eventType, dto.getId());
        }
    }
}
