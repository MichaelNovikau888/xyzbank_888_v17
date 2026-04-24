package com.bank.authorization.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Конфигурация топиков Kafka для сервиса авторизации
 */
@Configuration
public class KafkaTopicConfig {

 /**
     * Топик для получения запроса на вход в систему
 */
    @Bean
    public NewTopic authLoginTopic() {
        return TopicBuilder.name("auth.login").build();
    }

 /**
     * Топик для ответа на запрос входа в систему
 */
    @Bean
    public NewTopic authLoginResponseTopic() {
        return TopicBuilder.name("auth.login.response").build();
    }

 /**
     * Топик для получения запроса проверки валидности токена
 */
    @Bean
    public NewTopic authValidateTopic() {
        return TopicBuilder.name("auth.validate").build();
    }

 /**
     * Топик для ответа на запрос проверки валидности токена
 */
    @Bean
    public NewTopic authValidateResponseTopic() {
        return TopicBuilder.name("auth.validate.response").build();
    }

 /**
     * Топик для получения запроса о создании пользователя
 */
    @Bean
    public NewTopic userCreateTopic() {
        return TopicBuilder.name("user.create").build();
    }

 /**
     * Топик для ответа на запрос о создании пользователя
 */
    @Bean
    public NewTopic userCreateResponseTopic() {
        return TopicBuilder.name("user.create.response").build();
    }

 /**
     * Топик для запроса обновления данных пользователя
 */
    @Bean
    public NewTopic userUpdateTopic() {
        return TopicBuilder.name("user.update").build();
    }

 /**
     * Топик для ответа на запрос об обновлении данных пользователя
 */
    @Bean
    public NewTopic userUpdateResponseTopic() {
        return TopicBuilder.name("user.update.response").build();
    }

 /**
     * Топик для запроса удаления пользователя
 */
    @Bean
    public NewTopic userDeleteTopic() {
        return TopicBuilder.name("user.delete").build();
    }

 /**
     * Топик для ответа на запрос удаления пользователя
 */
    @Bean
    public NewTopic userDeleteResponseTopic() {
        return TopicBuilder.name("user.delete.response").build();
    }

 /**
     * Топик для запроса получения данных пользователя
 */
    @Bean
    public NewTopic userGetTopic() {
        return TopicBuilder.name("user.get").build();
    }

 /**
     * Топик для ответа на запрос получения данных пользователя
 */
    @Bean
    public NewTopic userGetResponseTopic() {
        return TopicBuilder.name("user.get.response").build();
    }

 /**
     * Топик для запроса получения всех пользователей
 */
    @Bean
    public NewTopic userGetAllTopic() {
        return TopicBuilder.name("user.get.all").build();
    }

 /**
     * Топик для ответа на запрос получение всех пользователей
 */
    @Bean
    public NewTopic userGetAllResponseTopic() {
        return TopicBuilder.name("user.get.all.response").build();
    }

 /**
     * Топик для логирования ошибок
 */
    @Bean
    public NewTopic errorLoggingTopic() {
        return TopicBuilder.name("error.logs").build();
    }
}
