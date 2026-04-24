package com.bank.publicinfo.producer;

import com.bank.publicinfo.dto.AuditDto;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Отправляет аудит-события в Kafka топик audit.logs → history-service.
 */
 /**
 * Аналог profile-service/AuditProducer.
 * Канал "audit-out" → topic задаётся в application.properties.
 */
@ApplicationScoped
public class AuditProducer {

    private static final Logger LOG = Logger.getLogger(AuditProducer.class);
    private static final String AUDIT_TOPIC = "audit.logs";

    @Inject
    @Channel("audit-out")
    Emitter<AuditDto> auditEmitter;

    public void sendAudit(AuditDto dto) {
        LOG.debugf("Sending audit event: entityType=%s, operationType=%s",
                   dto.getEntityType(), dto.getOperationType());
        auditEmitter.send(
                Message.of(dto).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withTopic(AUDIT_TOPIC)
                                .build()
                )
        );
    }
}
