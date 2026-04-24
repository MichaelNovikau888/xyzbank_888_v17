package com.bank.authorization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Единая схема сообщения в Kafka-топик {@code error.logs}.
 *
 * <p>Все сервисы проекта шлют одинаковую структуру — это позволяет
 * history-service и любым будущим консьюмерам десериализовать сообщения
 * без свитча по источнику.
 *
 * <p>Поля:
 * <ul>
 *   <li>{@code serviceName} — имя сервиса-отправителя (обязательно)</li>
 *   <li>{@code errorCode}   — машиночитаемый код: NOT_FOUND, VALIDATION_ERROR и т.д. (обязательно)</li>
 *   <li>{@code message}     — человекочитаемое описание (обязательно)</li>
 *   <li>{@code httpStatus}  — HTTP-статус если ошибка произошла в HTTP-контексте (опционально)</li>
 *   <li>{@code requestId}   — идентификатор запроса/транзакции для трассировки (опционально)</li>
 *   <li>{@code stackTrace}  — стек вызовов для 5xx-ошибок (опционально, только dev/staging)</li>
 *   <li>{@code occurredAt}  — момент возникновения ошибки (обязательно)</li>
 * </ul>
 *
 * <p>JSON-пример:
 * <pre>{@code
 * {
 *   "serviceName": "profile-service",
 *   "errorCode":   "NOT_FOUND",
 *   "message":     "Profile not found: 42",
 *   "httpStatus":  404,
 *   "requestId":   null,
 *   "stackTrace":  null,
 *   "occurredAt":  "2026-04-23T10:15:30"
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorEvent {

    // ── Стандартные коды ошибок ───────────────────────────────────────────────

    public static final String CODE_NOT_FOUND        = "NOT_FOUND";
    public static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String CODE_CONFLICT         = "CONFLICT";
    public static final String CODE_UNAUTHORIZED     = "UNAUTHORIZED";
    public static final String CODE_FORBIDDEN        = "FORBIDDEN";
    public static final String CODE_INTERNAL_ERROR   = "INTERNAL_ERROR";
    public static final String CODE_KAFKA_ERROR      = "KAFKA_ERROR";

    // ── Поля ─────────────────────────────────────────────────────────────────

    private String        serviceName;
    private String        errorCode;
    private String        message;
    private Integer       httpStatus;
    private String        requestId;
    private String        stackTrace;
    private LocalDateTime occurredAt;

    public ErrorEvent() {}

    private ErrorEvent(Builder b) {
        this.serviceName = b.serviceName;
        this.errorCode   = b.errorCode;
        this.message     = b.message;
        this.httpStatus  = b.httpStatus;
        this.requestId   = b.requestId;
        this.stackTrace  = b.stackTrace;
        this.occurredAt  = LocalDateTime.now();
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder(String serviceName, String errorCode, String message) {
        return new Builder(serviceName, errorCode, message);
    }

    public static final class Builder {
        private final String serviceName;
        private final String errorCode;
        private final String message;
        private Integer httpStatus;
        private String  requestId;
        private String  stackTrace;

        private Builder(String serviceName, String errorCode, String message) {
            this.serviceName = serviceName;
            this.errorCode   = errorCode;
            this.message     = message;
        }

        public Builder httpStatus(int httpStatus)   { this.httpStatus = httpStatus; return this; }
        public Builder requestId(String requestId)  { this.requestId = requestId;   return this; }
        public Builder stackTrace(String trace)     { this.stackTrace = trace;      return this; }

        public ErrorEvent build() { return new ErrorEvent(this); }
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String        getServiceName() { return serviceName; }
    public String        getErrorCode()   { return errorCode; }
    public String        getMessage()     { return message; }
    public Integer       getHttpStatus()  { return httpStatus; }
    public String        getRequestId()   { return requestId; }
    public String        getStackTrace()  { return stackTrace; }
    public LocalDateTime getOccurredAt()  { return occurredAt; }

    public void setServiceName(String v)        { this.serviceName = v; }
    public void setErrorCode(String v)          { this.errorCode = v; }
    public void setMessage(String v)            { this.message = v; }
    public void setHttpStatus(Integer v)        { this.httpStatus = v; }
    public void setRequestId(String v)          { this.requestId = v; }
    public void setStackTrace(String v)         { this.stackTrace = v; }
    public void setOccurredAt(LocalDateTime v)  { this.occurredAt = v; }
}
