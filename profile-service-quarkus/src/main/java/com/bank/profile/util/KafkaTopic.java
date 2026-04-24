package com.bank.profile.util;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Конфигурация Kafka-топиков.
 /
 * В Spring: @Value("${app.kafka.topic...}") в обычном @Component.
 * В Quarkus: @ConfigProperty(name="...") в CDI-бине (@ApplicationScoped).
 * Имена свойств те же, источник — application.properties.
 */
@ApplicationScoped
@Getter
public class KafkaTopic {

    @ConfigProperty(name = "app.kafka.consumer.group-id", defaultValue = "profile-group")
    String groupId;

    @ConfigProperty(name = "app.kafka.topic.profile.create", defaultValue = "profile.create")
    String topicProfileCreate;

    @ConfigProperty(name = "app.kafka.topic.profile.update", defaultValue = "profile.update")
    String topicProfileUpdate;

    @ConfigProperty(name = "app.kafka.topic.profile.delete", defaultValue = "profile.delete")
    String topicProfileDelete;

    @ConfigProperty(name = "app.kafka.topic.profile.get", defaultValue = "profile.get")
    String topicProfileGet;

    @ConfigProperty(name = "app.kafka.topic.profile.get-response", defaultValue = "profile.get-response")
    String topicProfileGetResponse;

    @ConfigProperty(name = "app.kafka.topic.account.create", defaultValue = "account.details.create")
    String topicAccountDetailsCreate;

    @ConfigProperty(name = "app.kafka.topic.account.update", defaultValue = "account.details.update")
    String topicAccountDetailsUpdate;

    @ConfigProperty(name = "app.kafka.topic.account.delete", defaultValue = "account.details.delete")
    String topicAccountDetailsDelete;

    @ConfigProperty(name = "app.kafka.topic.account.get", defaultValue = "account.details.get")
    String topicAccountDetailsGet;

    @ConfigProperty(name = "app.kafka.topic.account.get-response", defaultValue = "account.details.get-response")
    String topicAccountDetailsGetResponse;

    @ConfigProperty(name = "app.kafka.topic.audit", defaultValue = "audit.logs")
    String topicAudit;

    @ConfigProperty(name = "app.kafka.topic.error", defaultValue = "error.logs")
    String topicError;
}
