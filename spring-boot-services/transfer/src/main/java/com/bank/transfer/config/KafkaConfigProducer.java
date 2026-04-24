package com.bank.transfer.config;

import com.bank.transfer.exception.ErrorEvent;
import com.bank.transfer.exception.ErrorResponse;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfigProducer {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config}")
    private String saslJaasConfig;

    @Value("${spring.kafka.producer.key-serializer}")
    private String producerKeySerializer;

    @Value("${spring.kafka.producer.value-serializer}")
    private String producerValueSerializer;

    // Общий метод для создания конфигурации продюсера
    private Map<String, Object> producerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producerKeySerializer);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producerValueSerializer);
        configProps.put("security.protocol", securityProtocol);
        configProps.put("sasl.mechanism", saslMechanism);
        configProps.put("sasl.jaas.config", saslJaasConfig);
        return configProps;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, ErrorEvent> errorEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, ErrorEvent> errorEventKafkaTemplate() {
        return new KafkaTemplate<>(errorEventProducerFactory());
    }

    /** @deprecated Заменён на {@link #errorEventKafkaTemplate()} после унификации схемы ошибок. */
    @Deprecated(forRemoval = true)
    @Bean
    public ProducerFactory<String, ErrorResponse> errorProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /** @deprecated Заменён на {@link #errorEventKafkaTemplate()} после унификации схемы ошибок. */
    @Deprecated(forRemoval = true)
    @Bean
    public KafkaTemplate<String, ErrorResponse> errorKafkaTemplate() {
        return new KafkaTemplate<>(errorProducerFactory());
    }

 /**
     * String KafkaTemplate для OutboxRelayScheduler.
     * Payload уже сериализован в JSON при записи в outbox_events.
 */
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> props = new HashMap<>(producerConfigs());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }
}// NOTE: append manually — see str_replace below
