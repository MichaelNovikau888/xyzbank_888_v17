package com.bank.history.kafka;

import com.bank.history.dto.ErrorEvent;
import com.bank.history.entity.History;
import com.bank.history.metrics.HistoryMetrics;
import com.bank.history.service.HistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Kafka-консьюмер для централизованного хранения истории событий.
 /
 /**
 * Idempotency (at-least-once delivery):
 *   History получает raw String-события от всех сервисов. Дублирование
 *   возможно при перезапуске консьюмера или ребалансировке.
 */
 /**
 *   Стратегия: content-hash deduplication.
 *   SHA-256 хэш от (serviceName + eventType + eventData) вычисляется перед
 *   записью. Если запись с таким хэшем уже есть — событие пропускается.
 *   Уникальный индекс idx_history_content_hash в PostgreSQL — финальный guard.
 */
 /**
 *   Преимущество: не нужен внешний state (Redis/кэш) — всё в той же БД.
 *   Ограничение: два разных события с одинаковым payload → только первое.
 *   В реальности это исчезающе редко (включает timestamp в payload).
 */
 /**
 * DLQ: history-service получает и хранит сообщения из DLQ других сервисов.
 */
@ApplicationScoped
public class HistoryKafkaListener {

    private static final Logger log = Logger.getLogger(HistoryKafkaListener.class);

    @Inject
    HistoryService historyService;

    @Inject
    HistoryMetrics historyMetrics;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("audit-logs-in")
    @Blocking
    public void handleAuditLog(String message) {
        log.infof("Kafka [audit.logs] received");
        historyMetrics.getAuditLogsReceived().increment();
        saveEventIdempotent("audit.logs", "AUDIT", message);
    }

    @Incoming("transfer-events-in")
    @Blocking
    public void handleTransferEvent(String message) {
        log.infof("Kafka [transfer.events] received");
        historyMetrics.getTransferEventsReceived().increment();
        saveEventIdempotent("transfer-service", "TRANSFER", message);
    }

    @Incoming("account-events-in")
    @Blocking
    public void handleAccountEvent(String message) {
        log.infof("Kafka [account.events] received");
        historyMetrics.getAccountEventsReceived().increment();
        saveEventIdempotent("account-service", "ACCOUNT", message);
    }

    @Incoming("error-logs-in")
    @Blocking
    public void handleErrorLog(String message) {
        historyMetrics.getErrorLogsReceived().increment();

        // Десериализуем ErrorEvent чтобы использовать serviceName из payload
        // (все продюсеры теперь шлют единую схему ErrorEvent)
        String serviceName = "error.logs";
        String errorCode   = "ERROR";
        try {
            ErrorEvent event = objectMapper.readValue(message, ErrorEvent.class);
            if (event.getServiceName() != null) serviceName = event.getServiceName();
            if (event.getErrorCode()   != null) errorCode   = event.getErrorCode();
            log.warnf("Kafka [error.logs] received: service=%s code=%s msg=%s",
                      serviceName, errorCode, event.getMessage());
        } catch (Exception e) {
            // Если не распарсили — сохраняем raw строку (обратная совместимость)
            log.warnf("Kafka [error.logs] received (non-ErrorEvent payload): %s", message);
        }

        saveEventIdempotent(serviceName, errorCode, message);
    }

    // ── DLQ handlers ─────────────────────────────────────────────────────────

    @Incoming("audit-logs-dlq")
    @Blocking
    public void handleAuditLogDlq(String rawMessage) {
        log.errorf("DLQ [audit.logs]: %s", rawMessage);
        historyMetrics.getDlqEventsReceived().increment();
        saveEventIdempotent("DLQ", "DLQ_AUDIT", rawMessage);
    }

    @Incoming("error-logs-dlq")
    @Blocking
    public void handleErrorLogDlq(String rawMessage) {
        log.errorf("DLQ [error.logs]: %s", rawMessage);
        historyMetrics.getDlqEventsReceived().increment();
        saveEventIdempotent("DLQ", "DLQ_ERROR", rawMessage);
    }

    // ── private helpers ───────────────────────────────────────────────────────

 /**
     * Сохраняет событие идемпотентно.
     * Вычисляет SHA-256 хэш и пропускает дублирующее событие.
     * Финальный guard — уникальный индекс idx_history_content_hash в PostgreSQL:
     * если два потока одновременно прошли проверку, вторая INSERT упадёт с
     * ConstraintViolationException — это нормально, перехватываем и игнорируем.
 */
    private void saveEventIdempotent(String serviceName, String eventType, String data) {
        String hash = computeHash(serviceName, eventType, data);

        History history = new History();
        history.setServiceName(serviceName);
        history.setEventType(eventType);
        history.setEventData(data);
        history.setCreatedAt(LocalDateTime.now());
        history.setContentHash(hash);

        try {
            historyService.saveHistory(history);
            historyMetrics.getEventsSaved().increment();
        } catch (ConstraintViolationException e) {
            // Уникальный индекс сработал — это дубль, всё нормально
            log.debugf("Idempotency: duplicate hash=%s, skipping", hash);
            historyMetrics.getEventsIdempotentSkipped().increment();
        }
    }

 /**
     * ThreadLocal<MessageDigest> — переиспользуем digest на поток, избегая
     * MessageDigest.getInstance("SHA-256") на каждое сообщение (~2 мкс).
     * reset() обязателен перед каждым использованием.
 */
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    });

 /**
     * SHA-256 от конкатенации serviceName + eventType + eventData.
     * Детерминирован: одно и то же событие → один и тот же хэш.
     * HexFormat.of().formatHex() — JDK 17+, ~300 нс вместо StringBuilder+format.
 */
    private String computeHash(String serviceName, String eventType, String data) {
        MessageDigest digest = DIGEST.get();
        digest.reset();
        String input = serviceName + "|" + eventType + "|" + data;
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }
}
