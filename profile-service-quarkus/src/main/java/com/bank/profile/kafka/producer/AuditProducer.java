package com.bank.profile.kafka.producer;

import com.bank.profile.dto.AuditDto;
import com.bank.profile.util.KafkaTopic;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

/**
 * Отправляет аудит-события в Kafka.
 /
 * В Spring: KafkaTemplate<String, AuditDto>.send(topic, dto).
 * В Quarkus SmallRye: @Channel("channel-name") Emitter<T>.send(message).
 * Канал "audit-out" → topic задаётся в application.properties.
 */
@ApplicationScoped
public class AuditProducer {

    private static final Logger log = Logger.getLogger(AuditProducer.class);

    @Inject
    @Channel("audit-out")
    Emitter<AuditDto> auditEmitter;

    @Inject
    KafkaTopic topicsConfig;

    public void sendAudit(AuditDto dto) {
        log.debugf("Sending audit event: entityType=%s, operationType=%s", dto.getEntityType(), dto.getOperationType());
        auditEmitter.send(
                Message.of(dto).addMetadata(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withTopic(topicsConfig.getTopicAudit())
                                .build()
                )
        );
    }
}
