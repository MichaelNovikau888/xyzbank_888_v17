package com.bank.payment.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import com.bank.payment.event.PaymentCreatedEvent;
import com.bank.payment.event.PaymentStatusChangedEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

 /**
     * String KafkaTemplate — используется OutboxRelayScheduler.
     * Payload уже сериализован в JSON при записи в outbox_events.
 */
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    // Оставляем typed producers для PaymentStatusService
    private Map<String, Object> jsonProducerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, PaymentCreatedEvent> paymentCreatedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(jsonProducerConfigs());
    }

    @Bean
    public KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(paymentCreatedProducerFactory());
    }

    @Bean
    public ProducerFactory<String, PaymentStatusChangedEvent> paymentStatusChangedProducerFactory() {
        return new DefaultKafkaProducerFactory<>(jsonProducerConfigs());
    }

    @Bean
    public KafkaTemplate<String, PaymentStatusChangedEvent> statusChangedKafkaTemplate() {
        return new KafkaTemplate<>(paymentStatusChangedProducerFactory());
    }
}
