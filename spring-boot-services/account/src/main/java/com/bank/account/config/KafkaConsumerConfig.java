package com.bank.account.config;

import com.bank.account.dto.AccountDto;
import com.bank.account.dto.AuditDto;
import com.bank.account.exception.KafkaErrorSender;
import com.bank.account.exception.custom_exceptions.JsonProcessingException;
import com.bank.account.exception.custom_exceptions.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationException;
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
@Generated
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    @Value("${kafka.groups.account}")
    private String accountGroupId;

    @Value("${kafka.groups.audit}")
    private String auditGroupId;
    
    @Value("${kafka.topics.error-logs}")
    private String errorLogsTopic;

    private final ObjectMapper objectMapper;
    private final KafkaErrorSender kafkaErrorSender;

    @Bean
    public ConsumerFactory<String, AccountDto> accountConsumerFactory() {
        final Map<String, Object> props = setConsumerProps(accountGroupId);
        final JsonDeserializer<AccountDto> jsonDeserializer = new JsonDeserializer<>(AccountDto.class, objectMapper);
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConsumerFactory<String, AuditDto> auditConsumerFactory() {
        final Map<String, Object> props = setConsumerProps(auditGroupId);
        final JsonDeserializer<AuditDto> jsonDeserializer = new JsonDeserializer<>(AuditDto.class, objectMapper);
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConsumerFactory<String, Long> longConsumerFactory() {
        final Map<String, Object> props = setConsumerProps(accountGroupId);
        final JsonDeserializer<Long> jsonDeserializer = new JsonDeserializer<>(Long.class, objectMapper);
        return new DefaultKafkaConsumerFactory<>(props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(jsonDeserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Long> longKafkaListenerContainerFactory() {
        final ConcurrentKafkaListenerContainerFactory<String, Long> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(longConsumerFactory());
        factory.setCommonErrorHandler(createErrorHandler());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AccountDto> accountKafkaListenerContainerFactory() {
        final ConcurrentKafkaListenerContainerFactory<String, AccountDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(accountConsumerFactory());
        factory.setCommonErrorHandler(createErrorHandler());
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditDto> auditKafkaListenerContainerFactory() {
        final ConcurrentKafkaListenerContainerFactory<String, AuditDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory());
        factory.setCommonErrorHandler(createErrorHandler());
        return factory;
    }

    private Map<String, Object> setConsumerProps(String groupId) {
        final Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bank.account.dto");
        log.info("Creating consumer properties groupId: {}", groupId);
        return props;
    }

    private DefaultErrorHandler createErrorHandler() {
        final DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("Error processing record in topic {}: {}",
                            record.topic(), exception.getMessage());
                    kafkaErrorSender.sendError(exception, errorLogsTopic);
                },
                new FixedBackOff(1000L, 2L)
        );
        handler.addNotRetryableExceptions(
                SerializationException.class,
                JsonProcessingException.class,
                ValidationException.class,
                SecurityException.class
        );
        return handler;
    }
}
