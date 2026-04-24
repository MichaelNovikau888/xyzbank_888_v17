package com.bank.profile.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты единой схемы {@link ErrorEvent}.
 *
 * <p>Проверяет:
 * <ul>
 *   <li>Builder корректно заполняет все поля</li>
 *   <li>Обязательные поля (serviceName, errorCode, message, occurredAt)</li>
 *   <li>Опциональные поля (httpStatus, requestId, stackTrace) — null если не заданы</li>
 *   <li>JSON-сериализация: null-поля не включаются ({@code @JsonInclude(NON_NULL)})</li>
 *   <li>JSON-десериализация: обратный маппинг</li>
 *   <li>Константы кодов ошибок</li>
 * </ul>
 */
@QuarkusTest
@DisplayName("ErrorEvent — unified Kafka error schema")
class ErrorEventTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ── Builder ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("Builder")
    class BuilderTests {

        @Test @DisplayName("обязательные поля заполняются через builder")
        void requiredFields() {
            ErrorEvent event = ErrorEvent
                    .builder("profile-service", ErrorEvent.CODE_NOT_FOUND, "Profile not found: 42")
                    .build();

            assertThat(event.getServiceName()).isEqualTo("profile-service");
            assertThat(event.getErrorCode()).isEqualTo("NOT_FOUND");
            assertThat(event.getMessage()).isEqualTo("Profile not found: 42");
            assertThat(event.getOccurredAt()).isNotNull()
                    .isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test @DisplayName("опциональные поля null по умолчанию")
        void optionalFieldsNullByDefault() {
            ErrorEvent event = ErrorEvent
                    .builder("svc", ErrorEvent.CODE_INTERNAL_ERROR, "oops")
                    .build();

            assertThat(event.getHttpStatus()).isNull();
            assertThat(event.getRequestId()).isNull();
            assertThat(event.getStackTrace()).isNull();
        }

        @Test @DisplayName("httpStatus задаётся через builder")
        void httpStatus() {
            ErrorEvent event = ErrorEvent
                    .builder("svc", ErrorEvent.CODE_NOT_FOUND, "not found")
                    .httpStatus(404)
                    .build();
            assertThat(event.getHttpStatus()).isEqualTo(404);
        }

        @Test @DisplayName("requestId задаётся через builder")
        void requestId() {
            ErrorEvent event = ErrorEvent
                    .builder("svc", ErrorEvent.CODE_KAFKA_ERROR, "kafka fail")
                    .requestId("req-uuid-123")
                    .build();
            assertThat(event.getRequestId()).isEqualTo("req-uuid-123");
        }

        @Test @DisplayName("stackTrace задаётся через builder")
        void stackTrace() {
            ErrorEvent event = ErrorEvent
                    .builder("svc", ErrorEvent.CODE_INTERNAL_ERROR, "crash")
                    .stackTrace("at com.example.Foo.bar(Foo.java:42)")
                    .build();
            assertThat(event.getStackTrace()).contains("Foo.java");
        }

        @Test @DisplayName("occurredAt устанавливается автоматически при build()")
        void occurredAtAutoSet() {
            LocalDateTime before = LocalDateTime.now();
            ErrorEvent event = ErrorEvent
                    .builder("svc", ErrorEvent.CODE_VALIDATION_ERROR, "bad input")
                    .build();
            LocalDateTime after = LocalDateTime.now();

            assertThat(event.getOccurredAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }
    }

    // ── Коды ошибок ───────────────────────────────────────────────────────────

    @Nested @DisplayName("Стандартные коды ошибок")
    class ErrorCodes {

        @Test @DisplayName("все коды определены и не null")
        void allCodesNotNull() {
            assertThat(ErrorEvent.CODE_NOT_FOUND).isEqualTo("NOT_FOUND");
            assertThat(ErrorEvent.CODE_VALIDATION_ERROR).isEqualTo("VALIDATION_ERROR");
            assertThat(ErrorEvent.CODE_CONFLICT).isEqualTo("CONFLICT");
            assertThat(ErrorEvent.CODE_UNAUTHORIZED).isEqualTo("UNAUTHORIZED");
            assertThat(ErrorEvent.CODE_FORBIDDEN).isEqualTo("FORBIDDEN");
            assertThat(ErrorEvent.CODE_INTERNAL_ERROR).isEqualTo("INTERNAL_ERROR");
            assertThat(ErrorEvent.CODE_KAFKA_ERROR).isEqualTo("KAFKA_ERROR");
        }
    }

    // ── JSON-сериализация ─────────────────────────────────────────────────────

    @Nested @DisplayName("JSON serialization / deserialization")
    class JsonTests {

        @Test @DisplayName("null-поля не включаются в JSON (@JsonInclude NON_NULL)")
        void nullFieldsExcluded() throws Exception {
            ErrorEvent event = ErrorEvent
                    .builder("profile-service", ErrorEvent.CODE_NOT_FOUND, "Profile not found")
                    .httpStatus(404)
                    .build();

            String json = mapper.writeValueAsString(event);

            assertThat(json).contains("\"serviceName\"");
            assertThat(json).contains("\"errorCode\"");
            assertThat(json).contains("\"message\"");
            assertThat(json).contains("\"httpStatus\"");
            assertThat(json).contains("\"occurredAt\"");
            // Опциональные поля не заданы — не должны присутствовать
            assertThat(json).doesNotContain("\"requestId\"");
            assertThat(json).doesNotContain("\"stackTrace\"");
        }

        @Test @DisplayName("все поля сериализуются если заданы")
        void allFieldsSerialized() throws Exception {
            ErrorEvent event = ErrorEvent
                    .builder("transfer-service", ErrorEvent.CODE_CONFLICT, "Duplicate transfer")
                    .httpStatus(409)
                    .requestId("req-42")
                    .stackTrace("at Foo.bar:10")
                    .build();

            String json = mapper.writeValueAsString(event);

            assertThat(json).contains("transfer-service");
            assertThat(json).contains("CONFLICT");
            assertThat(json).contains("Duplicate transfer");
            assertThat(json).contains("409");
            assertThat(json).contains("req-42");
            assertThat(json).contains("at Foo.bar:10");
        }

        @Test @DisplayName("десериализация из JSON восстанавливает все поля")
        void deserializationRoundTrip() throws Exception {
            ErrorEvent original = ErrorEvent
                    .builder("account-service", ErrorEvent.CODE_INTERNAL_ERROR, "DB error")
                    .httpStatus(500)
                    .requestId("uuid-999")
                    .build();

            String json = mapper.writeValueAsString(original);
            ErrorEvent restored = mapper.readValue(json, ErrorEvent.class);

            assertThat(restored.getServiceName()).isEqualTo("account-service");
            assertThat(restored.getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(restored.getMessage()).isEqualTo("DB error");
            assertThat(restored.getHttpStatus()).isEqualTo(500);
            assertThat(restored.getRequestId()).isEqualTo("uuid-999");
            assertThat(restored.getOccurredAt()).isNotNull();
        }

        @Test @DisplayName("JSON разных сервисов десериализуется в одну схему")
        void crossServiceDeserialization() throws Exception {
            // Имитируем JSON от authorization-service
            String authServiceJson = """
                {
                  "serviceName": "authorization-service",
                  "errorCode":   "UNAUTHORIZED",
                  "message":     "Invalid credentials",
                  "requestId":   "req-auth-001",
                  "occurredAt":  "2026-04-23T10:15:30"
                }
                """;

            ErrorEvent event = mapper.readValue(authServiceJson, ErrorEvent.class);

            assertThat(event.getServiceName()).isEqualTo("authorization-service");
            assertThat(event.getErrorCode()).isEqualTo("UNAUTHORIZED");
            assertThat(event.getMessage()).isEqualTo("Invalid credentials");
            assertThat(event.getRequestId()).isEqualTo("req-auth-001");
            assertThat(event.getHttpStatus()).isNull();  // не было в JSON
        }
    }
}
