package com.bank.account.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka.topics")
@Data
public class KafkaTopicsConfig {
    private String accountCreate;
    private String accountUpdate;
    private String accountDelete;
    private String accountGet;
    private String accountGetById;
    private String errorLogs;
    private String auditLogs;
    private String externalAccountCreate;
    private String externalAccountUpdate;
    private String externalAccountDelete;
    private String externalAccountGet;
    private String externalAccountGetById;
    private String authValidate;
    private String authValidateResponse;
}
