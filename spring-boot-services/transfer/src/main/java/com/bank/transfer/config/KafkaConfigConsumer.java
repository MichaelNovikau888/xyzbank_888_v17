package com.bank.transfer.config;

import com.bank.transfer.antifraud.AntifraudResponseEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfigConsumer {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.security.protocol}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config}")
    private String saslJaasConfig;

    // ── Фабрика: ответы антифрода на проверки переводов ─────────────────────

 /**
     * ConsumerFactory для получения AntifraudResponseEvent от antifraud.
 */
 /**
     * Топик: transfer.antifraud.response
     * GroupId: transfer-antifraud-response-group
 */
 /**
     * Отдельная группа (не transfer-group) чтобы:
     * - Не смешивать offset-management с основным потреблением
     * - Легко масштабировать обработку antifraud-ответов независимо
 */
    @Bean
    public ConsumerFactory<String, AntifraudResponseEvent> transferAntifraudResponseConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "transfer-antifraud-response-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                  "com.bank.transfer.antifraud,com.bank.antifraud.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                  "com.bank.transfer.antifraud.AntifraudResponseEvent");
        props.put("security.protocol", securityProtocol);
        props.put("sasl.mechanism",    saslMechanism);
        props.put("sasl.jaas.config",  saslJaasConfig);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(AntifraudResponseEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AntifraudResponseEvent>
            transferAntifraudResponseListenerContainerFactory(
                    ConsumerFactory<String, AntifraudResponseEvent> transferAntifraudResponseConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, AntifraudResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transferAntifraudResponseConsumerFactory);
        return factory;
    }
}
