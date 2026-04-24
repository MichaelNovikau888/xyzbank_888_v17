package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.BranchDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер Branch-событий (create / update / delete).
 */
 /**
 * Топик: public-info.branch.events
 * Канал: branch-events-out → задаётся в application.properties.
 */
 /**
 * Используется в BranchServiceImpl после каждой мутирующей операции,
 * чтобы downstream-сервисы (history, audit) получили событие.
 */
@ApplicationScoped
public class BranchProducer {

    private static final Logger LOG = Logger.getLogger(BranchProducer.class);
    private static final String TOPIC = "public-info.branch.events";

    @Inject
    @Channel("branch-events-out")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

 /**
     * Отправляет событие о создании Branch.
 */
    public void sendCreated(BranchDto dto) {
        send("CREATED", dto);
    }

 /**
     * Отправляет событие об обновлении Branch.
 */
    public void sendUpdated(BranchDto dto) {
        send("UPDATED", dto);
    }

 /**
     * Отправляет событие об удалении Branch (только id).
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
            LOG.infof("Branch DELETED event sent: id=%d", id);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send Branch DELETED event for id=%d", id);
        }
    }

    private void send(String eventType, BranchDto dto) {
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
            LOG.infof("Branch %s event sent: id=%d", eventType, dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send Branch %s event for id=%d", eventType, dto.getId());
        }
    }
}
