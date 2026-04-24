package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.LicenseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер License-событий (create / update / delete).
 */
 /**
 * Топик: public-info.license.events
 * Канал: license-events-out → задаётся в application.properties.
 */
 /**
 * Используется в LicenseServiceImpl после каждой мутирующей операции,
 * чтобы downstream-сервисы (history, audit) получили событие.
 */
@ApplicationScoped
public class LicenseProducer {

    private static final Logger LOG = Logger.getLogger(LicenseProducer.class);
    private static final String TOPIC = "public-info.license.events";

    @Inject
    @Channel("license-events-out")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

 /**
     * Отправляет событие о создании License.
 */
    public void sendCreated(LicenseDto dto) {
        send("CREATED", dto);
    }

 /**
     * Отправляет событие об обновлении License.
 */
    public void sendUpdated(LicenseDto dto) {
        send("UPDATED", dto);
    }

 /**
     * Отправляет событие об удалении License (только id).
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
            LOG.infof("License DELETED event sent: id=%d", id);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send License DELETED event for id=%d", id);
        }
    }

    private void send(String eventType, LicenseDto dto) {
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
            LOG.infof("License %s event sent: id=%d", eventType, dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send License %s event for id=%d", eventType, dto.getId());
        }
    }
}
