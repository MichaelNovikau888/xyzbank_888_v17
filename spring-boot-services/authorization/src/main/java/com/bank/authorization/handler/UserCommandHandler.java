package com.bank.authorization.handler;

import com.bank.authorization.dto.KafkaRequest;
import com.bank.authorization.dto.KafkaResponse;
import com.bank.authorization.dto.UserDto;
import com.bank.authorization.repository.UserRepository;
import com.bank.authorization.metrics.AuthMetrics;
import com.bank.authorization.service.UserService;
import com.bank.authorization.utils.JwtValidator;
import com.bank.authorization.utils.ResponseFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Обработчик Kafka-команд для управления пользователями.
 /
 * Idempotency (at-least-once delivery):
/
 *   CREATE — проверяем findByProfileId перед созданием.
 *            profileId — натуральный внешний ключ пользователя (приходит от profile-service).
 *            Если пользователь уже существует — возвращаем его без дублирования.
 /
 *   UPDATE — идемпотентен: повторное применение тех же данных не меняет итог.
 *            Но проверяем existsById — не создаём заново при повторной доставке.
 /
 *   DELETE — UserServiceImpl уже содержит idempotency-guard (existsById).
 *            Здесь дополнительная проверка не нужна: сервис не бросает исключение.
 /
 *   GET / GET_ALL — всегда идемпотентны, никакой guard не нужен.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandHandler {

    @Value("${topics.user_create_response}") private String userCreateResponseTopic;
    @Value("${topics.user_update_response}") private String userUpdateResponseTopic;
    @Value("${topics.user_delete_response}") private String userDeleteResponseTopic;
    @Value("${topics.user_get_response}")    private String userGetResponseTopic;
    @Value("${topics.user_get_all_response}") private String userGetAllResponseTopic;

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, KafkaResponse> kafkaTemplate;
    private final ResponseFactory responseFactory;
    private final JwtValidator jwtValidator;
    private final AuthMetrics authMetrics;

    @Timed("kafka_handleCreateUser")
    public void handleCreateUser(KafkaRequest request) {
        KafkaResponse response;
        try {
            jwtValidator.validate(request.getJwtToken(), "ROLE_ADMIN");
            UserDto userDto = objectMapper.readValue(request.getPayload(), UserDto.class);

            // ── Idempotency: CREATE ──────────────────────────────────────────
            if (userDto.getProfileId() != null) {
                var existing = userRepository.findByProfileId(userDto.getProfileId());
                if (existing.isPresent()) {
                    log.warn("Idempotency: user with profileId={} already exists, returning existing",
                            userDto.getProfileId());
                    authMetrics.getUserIdempotentSkipped().increment();
                    response = responseFactory.createSuccessResponse(
                            request.getRequestId(), "User already exists (idempotent)", existing.get());
                    kafkaTemplate.send(userCreateResponseTopic, response);
                    return;
                }
            }
            // ────────────────────────────────────────────────────────────────

            UserDto created = userService.save(userDto);
            authMetrics.getUserCreated().increment();
            response = responseFactory.createSuccessResponse(request.getRequestId(), "User created successfully", created);

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON in create payload: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Invalid JSON format");
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error creating user: " + e.getMessage());
        }
        kafkaTemplate.send(userCreateResponseTopic, response);
    }

    @Timed("kafka_handleUpdateUser")
    public void handleUpdateUser(KafkaRequest request) {
        KafkaResponse response;
        try {
            jwtValidator.validate(request.getJwtToken(), "ROLE_ADMIN");
            UserDto userDto = objectMapper.readValue(request.getPayload(), UserDto.class);

            // ── Idempotency: UPDATE — проверяем что пользователь существует ──
            if (userDto.getId() != null && !userRepository.existsById(userDto.getId())) {
                log.warn("Idempotency: user id={} not found for update, skipping", userDto.getId());
                response = responseFactory.createErrorResponse(request.getRequestId(),
                        "User not found: " + userDto.getId());
                kafkaTemplate.send(userUpdateResponseTopic, response);
                return;
            }
            // ────────────────────────────────────────────────────────────────

            UserDto updated = userService.updateUser(userDto.getId(), userDto);
            authMetrics.getUserUpdated().increment();
            response = responseFactory.createSuccessResponse(request.getRequestId(), "User updated successfully", updated);

        } catch (JsonProcessingException e) {
            log.error("Invalid JSON in update payload: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Invalid JSON format");
        } catch (Exception e) {
            log.error("Error updating user: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error updating user: " + e.getMessage());
        }
        kafkaTemplate.send(userUpdateResponseTopic, response);
    }

    @Timed("kafka_handleDeleteUser")
    public void handleDeleteUser(KafkaRequest request) {
        KafkaResponse response;
        try {
            jwtValidator.validate(request.getJwtToken(), "ROLE_ADMIN");
            Long userId = Long.valueOf(request.getPayload());
            // UserServiceImpl.deleteById уже содержит idempotency-guard (existsById)
            userService.deleteById(userId);
            authMetrics.getUserDeleted().increment();
            response = responseFactory.createSuccessResponse(request.getRequestId(), "User deleted successfully", null);
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error deleting user: " + e.getMessage());
        }
        kafkaTemplate.send(userDeleteResponseTopic, response);
    }

    @Timed("kafka_handleGetUser")
    public void handleGetUser(KafkaRequest request) {
        KafkaResponse response;
        try {
            jwtValidator.validate(request.getJwtToken(), "ROLE_ADMIN");
            Long userId = Long.valueOf(request.getPayload());
            UserDto user = userService.getUserById(userId);
            response = responseFactory.createSuccessResponse(request.getRequestId(), "User retrieved successfully", user);
        } catch (Exception e) {
            log.error("Error retrieving user: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error retrieving user: " + e.getMessage());
        }
        kafkaTemplate.send(userGetResponseTopic, response);
    }

    @Timed("kafka_handleGetAllUsers")
    public void handleGetAllUsers(KafkaRequest request) {
        KafkaResponse response;
        try {
            jwtValidator.validate(request.getJwtToken(), "ROLE_ADMIN");
            response = responseFactory.createSuccessResponse(request.getRequestId(), "Users retrieved successfully",
                    userService.getAllUsers());
        } catch (Exception e) {
            log.error("Error retrieving all users: {}", e.getMessage());
            response = responseFactory.createErrorResponse(request.getRequestId(), "Error retrieving all users: " + e.getMessage());
        }
        kafkaTemplate.send(userGetAllResponseTopic, response);
    }
}
