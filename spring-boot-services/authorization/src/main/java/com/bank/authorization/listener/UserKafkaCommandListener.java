package com.bank.authorization.listener;

import com.bank.authorization.handler.AuthCommandHandler;
import com.bank.authorization.handler.UserCommandHandler;
import com.bank.authorization.dto.AuthRequest;
import com.bank.authorization.dto.KafkaRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserKafkaCommandListener {

    private final AuthCommandHandler authCommandHandler;
    private final UserCommandHandler userCommandHandler;

    @KafkaListener(topics = "${topics.auth_login}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeLoginRequest(AuthRequest request) {
        authCommandHandler.handleLogin(request);
    }

    @KafkaListener(topics = "${topics.auth_validate}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeTokenValidationRequest(KafkaRequest request) {
        authCommandHandler.handleTokenValidation(request);
    }

    @KafkaListener(topics = "${topics.user_create}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeCreateUserRequest(KafkaRequest request) {
        userCommandHandler.handleCreateUser(request);
    }

    @KafkaListener(topics = "${topics.user_update}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeUpdateUserRequest(KafkaRequest request) {
        userCommandHandler.handleUpdateUser(request);
    }

    @KafkaListener(topics = "${topics.user_delete}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeDeleteUserRequest(KafkaRequest request) {
        userCommandHandler.handleDeleteUser(request);
    }

    @KafkaListener(topics = "${topics.user_get}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeGetUserRequest(KafkaRequest request) {
        userCommandHandler.handleGetUser(request);
    }

    @KafkaListener(topics = "${topics.user_get_all}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeGetAllUsersRequest(KafkaRequest request) {
        userCommandHandler.handleGetAllUsers(request);
    }
}
