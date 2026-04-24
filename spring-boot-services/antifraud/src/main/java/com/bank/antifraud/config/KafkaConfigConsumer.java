package com.bank.antifraud.config;

import com.bank.antifraud.dto.AuditDto;
import com.bank.antifraud.dto.AntifraudRequestEvent;
import com.bank.antifraud.dto.TransferAntifraudRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConfigConsumer {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.group-id}")
    private String groupId;

    // ── Существующие фабрики ──────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> genericConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(Object.class));
    }

    @Bean
    public ConsumerFactory<String, AuditDto> auditConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bank.antifraud.dto.AuditDto");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(AuditDto.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditDto>
            auditKafkaListenerContainerFactory(
                    ConsumerFactory<String, AuditDto> auditConsumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AuditDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
            suspiciousKafkaListenerContainerFactory(
                    ConsumerFactory<String, Object> genericConsumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(genericConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

 /**
     * Factory для приёма AntifraudRequestEvent от payment-api.
     * Топик: payment.antifraud.check
     * GroupId: anti_fraud-group
 */
    @Bean
    public ConsumerFactory<String, AntifraudRequestEvent> antifraudRequestConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "anti_fraud-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                  "com.bank.antifraud.dto,com.bank.payment.event");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(AntifraudRequestEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AntifraudRequestEvent>
            antifraudRequestListenerContainerFactory(
                    ConsumerFactory<String, AntifraudRequestEvent> antifraudRequestConsumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AntifraudRequestEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(antifraudRequestConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ── НОВАЯ фабрика: проверки переводов от transfer-service ─────────────────

 /**
     * Factory для приёма TransferAntifraudRequestEvent от transfer-service.
 */
 /**
     * Топик: transfer.antifraud.check
     * GroupId: anti_fraud-transfer-group  ← отдельная группа от payment-api
 */
 /**
     * Отдельная группа необходима: два разных топика с разными payload-типами
     * (AntifraudRequestEvent vs TransferAntifraudRequestEvent) не должны
     * смешиваться в одной consumer group.
 */
    @Bean
    public ConsumerFactory<String, TransferAntifraudRequestEvent>
            antifraudTransferCheckConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "anti_fraud-transfer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                  "com.bank.antifraud.dto,com.bank.transfer.antifraud");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(TransferAntifraudRequestEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferAntifraudRequestEvent>
            antifraudTransferCheckListenerContainerFactory(
                    ConsumerFactory<String, TransferAntifraudRequestEvent>
                            antifraudTransferCheckConsumerFactory,
                    DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TransferAntifraudRequestEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(antifraudTransferCheckConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // ── Error handler (общий) ─────────────────────────────────────────────────

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) -> log.error("Ошибка обработки сообщения: {}", record.value()),
                new FixedBackOff(1000L, 2L)
        );
    }
}
