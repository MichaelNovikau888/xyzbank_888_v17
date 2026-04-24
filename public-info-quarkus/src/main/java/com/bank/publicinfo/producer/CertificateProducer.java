package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.CertificateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Продюсер Certificate-событий (create / update / delete).
 */
 /**
 * Топик: public-info.certificate.events
 * Канал: certificate-events-out → задаётся в application.properties.
 */
 /**
 * Используется в CertificateServiceImpl после каждой мутирующей операции,
 * чтобы downstream-сервисы (history, audit) получили событие.
 */
@ApplicationScoped
public class CertificateProducer {

    private static final Logger LOG = Logger.getLogger(CertificateProducer.class);
    private static final String TOPIC = "public-info.certificate.events";

    @Inject
    @Channel("certificate-events-out")
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

 /**
     * Отправляет событие о создании Certificate.
 */
    public void sendCreated(CertificateDto dto) {
        send("CREATED", dto);
    }

 /**
     * Отправляет событие об обновлении Certificate.
 */
    public void sendUpdated(CertificateDto dto) {
        send("UPDATED", dto);
    }

 /**
     * Отправляет событие об удалении Certificate (только id).
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
            LOG.infof("Certificate DELETED event sent: id=%d", id);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send Certificate DELETED event for id=%d", id);
        }
    }

    private void send(String eventType, CertificateDto dto) {
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
            LOG.infof("Certificate %s event sent: id=%d", eventType, dto.getId());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send Certificate %s event for id=%d", eventType, dto.getId());
        }
    }
}
