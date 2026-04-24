package com.bank.account.config;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.dto.KafkaRequest;
import com.bank.account.event.CardEvent;
import com.bank.account.exception.error_dto.ErrorEvent;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableKafka
@Generated
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, AccountDto> accountProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    @Bean
    public ProducerFactory<String, AuditDto> auditProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    @Bean
    public ProducerFactory<String, KafkaRequest> kafkaRequestProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    @Bean
    public ProducerFactory<String, List<AccountDto>> accountsListProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    @Bean
    public ProducerFactory<String, ErrorEvent> errorEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    @Bean
    public ProducerFactory<String, CardEvent> cardEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(setProducerProps());
    }

    // String producer — использует StringSerializer для value (не JsonSerializer).
    // Нужен OutboxRelayScheduler и AccountProducer.sendTextMessage().
    // Payload уже сериализован в JSON при записи в outbox_events.
    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, KafkaRequest> kafkaRequestKafkaTemplate() {
        return new KafkaTemplate<>(kafkaRequestProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, List<AccountDto>> accountsListKafkaTemplate() {
        return new KafkaTemplate<>(accountsListProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, AccountDto> accountKafkaTemplate() {
        return new KafkaTemplate<>(accountProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, ErrorEvent> errorEventKafkaTemplate() {
        return new KafkaTemplate<>(errorEventProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, AuditDto> auditKafkaTemplate() {
        return new KafkaTemplate<>(auditProducerFactory());
    }

    @Bean
    public KafkaTemplate<String, CardEvent> cardEventKafkaTemplate() {
        return new KafkaTemplate<>(cardEventProducerFactory());
    }

    private Map<String, Object> setProducerProps() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return configProps;
    }
}
