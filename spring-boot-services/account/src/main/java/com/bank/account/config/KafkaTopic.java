package com.bank.account.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Создает топики для Кафки
 */
@Configuration
@RequiredArgsConstructor
public class KafkaTopic {

    @Value("${spring.kafka.partitions}")
    private int partitions;

    @Value("${spring.kafka.replicas}")
    private short replicas;

    private final KafkaTopicsConfig kafkaTopicsConfig;

 /**
     * 'account.create' - топик для получения запроса из другого сервиса на создание нового аккаунта;
     * 'account.update' - Топик для получения запроса на обновление данных аккаунта из другого сервиса;
     * 'account.delete' - Топик для получения запроса на удаление аккаунта из другого сервиса;
     * 'account.get' - Топик для получения запроса на выдачу списка всех аккаунтов от другого сервиса;
     * 'account.getById' - Топик для получения запроса на выдачу конкретного аккаунта по id от стороннего сервиса;
     * 'audit.logs' - Топик для получения запроса на отправку логов;
     * 'external.account.create' - Топик для отправки результатов сохранения нового аккаунта другому сервису;
     * 'external.account.update' - Топик для отправки результатов изменения данных аккаунта другому сервису;
     * 'external.account.delete' - Топик для получения результата выполнения удаления аккаунта другим сервисом;
     * 'external.account.get' - Топик для получения результата запроса на выдачу списка всех аккаунтов другому сервису;
     * 'external.account.getById' - Топик для получения результата выполнения запроса на выдачу конкретного аккаунта
     *  по id другому сервису;
 */
 /**
     * 'error.logs' - Топик для получения записей ошибок;
 */
 /**
 */
    @Bean
    public List<NewTopic> kafkaTopics() {
        return Stream.of(kafkaTopicsConfig.getAccountCreate(),
                kafkaTopicsConfig.getAccountUpdate(),
                kafkaTopicsConfig.getAccountDelete(),
                kafkaTopicsConfig.getAccountGet(),
                kafkaTopicsConfig.getAccountGetById(),
                kafkaTopicsConfig.getExternalAccountCreate(),
                kafkaTopicsConfig.getExternalAccountUpdate(),
                kafkaTopicsConfig.getExternalAccountDelete(),
                kafkaTopicsConfig.getExternalAccountGet(),
                kafkaTopicsConfig.getExternalAccountGetById(),
                kafkaTopicsConfig.getAuditLogs(),
                kafkaTopicsConfig.getErrorLogs(),
                kafkaTopicsConfig.getAuthValidate(),
                kafkaTopicsConfig.getAuthValidateResponse()
                )
                .map(this::createTopic)
                .collect(Collectors.toList());
    }

    private NewTopic createTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
