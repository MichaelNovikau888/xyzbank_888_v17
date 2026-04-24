package com.bank.notification.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import com.bank.notification.client.FcmClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис отправки push-уведомлений через FCM.
 */
 /**
 * Поддерживает платежи (payment-api) и переводы (transfer-service).
 * Push-токены: Redis push:token:{clientId}, TTL=30 дней.
 */
@ApplicationScoped
public class PushNotificationService {

    private static final Logger LOG = Logger.getLogger(PushNotificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofDays(30);

    @Inject RedisDataSource redisDataSource;

    @Inject
    @RestClient
    FcmClient fcmClient;

    private ValueCommands<String, String> redisCommands;

    @PostConstruct
    void init() {
        redisCommands = redisDataSource.value(String.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ПЛАТЕЖИ
    // ══════════════════════════════════════════════════════════════════════════

    public void sendPaymentCreatedPush(String clientId, Long paymentId,
                                       BigDecimal amount, String currency) {
        String title = "💳 Платёж создан";
        String body  = String.format("Платёж №%d на сумму %.2f %s успешно создан",
                                     paymentId, amount, currency);
        sendToClient(clientId, title, body, paymentData(paymentId, "CREATED"));
    }

    public void sendPaymentStatusChangedPush(String clientId, Long paymentId,
                                              String newStatus, String reason) {
        String title = getPaymentTitle(newStatus);
        String body  = buildPaymentBody(paymentId, newStatus, reason);
        sendToClient(clientId, title, body, paymentData(paymentId, newStatus));
    }

 /**
     * Перегрузка с готовым body от {@link com.bank.notification.service.PaymentNotificationFormatter}.
     * Используется в NotificationConsumer для банковского формата уведомлений.
 */
    public void sendPaymentCreatedPush(String clientId, Long paymentId,
                                       BigDecimal amount, String currency,
                                       String formattedBody) {
        String title = "💳 Платёж создан";
        String body  = (formattedBody != null && !formattedBody.isBlank())
                ? formattedBody
                : String.format("Платёж №%d на сумму %.2f %s успешно создан",
                                paymentId, amount, currency);
        sendToClient(clientId, title, body, paymentData(paymentId, "CREATED"));
    }

 /**
     * Перегрузка с готовым body от {@link com.bank.notification.service.PaymentNotificationFormatter}.
 */
    public void sendPaymentStatusChangedPush(String clientId, Long paymentId,
                                              String newStatus, String reason,
                                              String formattedBody) {
        String title = getPaymentTitle(newStatus);
        String body  = (formattedBody != null && !formattedBody.isBlank())
                ? formattedBody
                : buildPaymentBody(paymentId, newStatus, reason);
        sendToClient(clientId, title, body, paymentData(paymentId, newStatus));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ПЕРЕВОДЫ — НОВЫЕ МЕТОДЫ
    // ══════════════════════════════════════════════════════════════════════════

 /**
     * Push «Перевод на проверке».
     * Отправляется при статусе REVIEW (антифрод-проверка, сумма 10k–50k).
     * Email не отправляется — промежуточный статус, финал придёт отдельным событием.
 */
    public void sendTransferReviewPush(String clientId, Long transferId,
                                        BigDecimal amount, String currency) {
        String amountStr = amount != null
                ? String.format("%.2f %s", amount, currency != null ? currency : "RUB")
                : "";
        String title = "🔍 Перевод на проверке";
        String body  = String.format(
                "Перевод №%d на сумму %s отправлен на проверку — ожидайте до 24 часов",
                transferId, amountStr);
        sendToClient(clientId, title, body, transferData(transferId, "REVIEW", "ACCOUNT"));
        LOG.infof("Push TRANSFER REVIEW: client_id=%s transfer_id=%d", clientId, transferId);
    }

 /**
     * Push «Перевод создан».
     * Отправляется сразу после сохранения перевода в БД.
 */
    public void sendTransferCreatedPush(String clientId, Long transferId,
                                         String transferType,
                                         BigDecimal amount, String currency,
                                         String recipientDisplay) {
        String title = getTransferTypeIcon(transferType) + " Перевод создан";
        String body  = String.format("Перевод №%d на сумму %.2f %s → %s",
                                     transferId, amount, currency, recipientDisplay);
        sendToClient(clientId, title, body, transferData(transferId, "CREATED", transferType));
        LOG.infof("Push TRANSFER CREATED: client_id=%s transfer_id=%d", clientId, transferId);
    }

 /**
     * Push о финальном статусе перевода: COMPLETED / BLOCKED / CANCELLED.
 */
    public void sendTransferFinalPush(String clientId, Long transferId,
                                       String transferType, String status,
                                       BigDecimal amount, String currency,
                                       String reason) {
        String title = getTransferFinalTitle(status, transferType);
        String body  = buildTransferFinalBody(transferId, status, amount, currency, reason);
        sendToClient(clientId, title, body, transferData(transferId, status, transferType));
        LOG.infof("Push TRANSFER %s: client_id=%s transfer_id=%d", status, clientId, transferId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // КАРТЫ
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // РЕГИСТРАЦИЯ (authorization-service)
    // ══════════════════════════════════════════════════════════════════════════

 /**
     * Push «Добро пожаловать» при регистрации нового клиента.
 */
 /**
     * <p>Best-effort: у нового пользователя push-токен может ещё не быть зарегистрирован.
     * Если токен отсутствует — push молча пропускается (не ошибка).
 */
 /**
     * @param clientId  ID пользователя (String)
     * @param fullName  полное имя для персонализации
 */
    public void sendWelcomePush(String clientId, String fullName) {
        String firstName = (fullName != null && !fullName.isBlank())
                ? fullName.trim().split("\\s+")[0]
                : "Клиент";
        String title = "🏦 Добро пожаловать в XYZ-Bank!";
        String body  = "Здравствуйте, " + firstName + "! Ваш аккаунт успешно создан.";
        sendToClient(clientId, title, body, java.util.Map.of("type", "REGISTRATION"));
    }

    public void sendCardCreatedPush(String clientId, Long cardId, String maskedNumber) {
        String title = "💳 Карта выпущена";
        String body  = String.format("Ваша карта %s готова к использованию", maskedNumber);
        sendToClient(clientId, title, body, cardData(cardId, "CREATED"));
        LOG.infof("Push CARD CREATED: client_id=%s card_id=%d", clientId, cardId);
    }

    public void sendCardBlockedPush(String clientId, Long cardId, String maskedNumber) {
        String title = "🔒 Карта заблокирована";
        String body  = String.format("Карта %s заблокирована. Обратитесь в банк", maskedNumber);
        sendToClient(clientId, title, body, cardData(cardId, "BLOCKED"));
        LOG.infof("Push CARD BLOCKED: client_id=%s card_id=%d", clientId, cardId);
    }

    public void sendCardUnblockedPush(String clientId, Long cardId, String maskedNumber) {
        String title = "🔓 Карта разблокирована";
        String body  = String.format("Карта %s снова активна", maskedNumber);
        sendToClient(clientId, title, body, cardData(cardId, "UNBLOCKED"));
        LOG.infof("Push CARD UNBLOCKED: client_id=%s card_id=%d", clientId, cardId);
    }

    public void sendCardLimitChangedPush(String clientId, Long cardId, String maskedNumber,
                                          java.math.BigDecimal dailyLimit,
                                          java.math.BigDecimal monthlyLimit) {
        String title = "⚙️ Лимит по карте изменён";
        String body  = String.format("Карта %s: дневной лимит %.0f ₽, месячный %.0f ₽",
                                     maskedNumber, dailyLimit, monthlyLimit);
        sendToClient(clientId, title, body, cardData(cardId, "LIMIT_CHANGED"));
        LOG.infof("Push CARD LIMIT_CHANGED: client_id=%s card_id=%d", clientId, cardId);
    }

    // ── Token management ──────────────────────────────────────────────────────

    public void registerPushToken(String clientId, String pushToken, String deviceType) {
        redisCommands.setex("push:token:" + clientId, TOKEN_TTL.getSeconds(), pushToken);
        LOG.infof("Push token registered: client_id=%s device=%s", clientId, deviceType);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void sendToClient(String clientId, String title, String body,
                               Map<String, String> data) {
        String token = getClientPushToken(clientId);
        if (token == null) {
            LOG.warnf("Push token not found for client_id=%s, skipping push", clientId);
            return;
        }
        sendPush(token, title, body, data);
    }

    private String getClientPushToken(String clientId) {
        String token = redisCommands.get("push:token:" + clientId);
        if (token != null) return token;
        return fetchPushTokenFromProfile(clientId);
    }

    private String fetchPushTokenFromProfile(String clientId) {
        // Push-токены НЕ хранятся в profile-service.
        // Токен регистрируется мобильным клиентом при логине через
        // POST /api/push/token → PushNotificationService.registerPushToken()
        // и хранится в Redis «push:token:{clientId}» TTL=30 дней.
        //
        // Если токена нет в Redis — клиент не авторизован на устройстве
        // или не дал разрешение на push-уведомления. Это нормальная ситуация.
        return null;
    }

    private void sendPush(String token, String title, String body,
                           Map<String, String> data) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("body",  body);
            notification.put("sound", "default");

            Map<String, Object> payload = new HashMap<>();
            payload.put("to",           token);
            payload.put("notification", notification);
            payload.put("data",         data);
            payload.put("priority",     "high");

            fcmClient.send(payload);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send push to token=%s", maskToken(token));
        }
    }

    // ── Text builders ─────────────────────────────────────────────────────────

    private String getPaymentTitle(String status) {
        return switch (status) {
            case "PROCESSING" -> "⏳ Платёж в обработке";
            case "COMPLETED"  -> "✅ Платёж выполнен";
            case "FAILED"     -> "❌ Платёж заблокирован";
            case "CANCELLED"  -> "🚫 Платёж отменён";
            default           -> "📱 Статус платежа изменён";
        };
    }

    private String buildPaymentBody(Long paymentId, String status, String reason) {
        return switch (status) {
            case "PROCESSING" -> String.format("Платёж №%d в обработке", paymentId);
            case "COMPLETED"  -> String.format("Платёж №%d выполнен ✅", paymentId);
            case "FAILED"     -> String.format("Платёж №%d заблокирован ❌%s",
                                     paymentId, reason != null ? ": " + reason : "");
            case "CANCELLED"  -> String.format("Платёж №%d отменён%s",
                                     paymentId, reason != null ? ": " + reason : "");
            default           -> String.format("Платёж №%d: %s", paymentId, status);
        };
    }

    private String getTransferTypeIcon(String transferType) {
        if (transferType == null) return "💸";
        return switch (transferType) {
            case "CARD"  -> "💳";
            case "PHONE" -> "📱";
            default      -> "🏦";
        };
    }

    private String getTransferFinalTitle(String status, String transferType) {
        String icon = getTransferTypeIcon(transferType);
        return switch (status) {
            case "COMPLETED"  -> icon + " Перевод выполнен";
            case "BLOCKED"    -> "❌ Перевод заблокирован";
            case "CANCELLED"  -> "🚫 Перевод отменён";
            case "REVIEW"     -> "🔍 Перевод на проверке";
            default           -> icon + " Статус перевода изменён";
        };
    }

    private String buildTransferFinalBody(Long transferId, String status,
                                           BigDecimal amount, String currency,
                                           String reason) {
        String amountStr = amount != null
                ? String.format("%.2f %s", amount, currency != null ? currency : "RUB")
                : "";
        return switch (status) {
            case "COMPLETED" -> String.format("Перевод №%d на сумму %s выполнен ✅",
                                              transferId, amountStr);
            case "BLOCKED"   -> String.format("Перевод №%d заблокирован ❌%s",
                                              transferId, reason != null ? ": " + reason : "");
            case "CANCELLED" -> String.format("Перевод №%d отменён%s",
                                              transferId, reason != null ? ": " + reason : "");
            default          -> String.format("Перевод №%d: %s", transferId, status);
        };
    }

    private Map<String, String> paymentData(Long paymentId, String status) {
        return Map.of("type", "payment", "payment_id", paymentId.toString(),
                      "status", status, "action", "open_payment_details");
    }

    private Map<String, String> transferData(Long transferId, String status, String transferType) {
        return Map.of("type", "transfer", "transfer_id", transferId.toString(),
                      "status", status, "transfer_type", transferType != null ? transferType : "ACCOUNT",
                      "action", "open_transfer_details");
    }

    private Map<String, String> cardData(Long cardId, String eventType) {
        return Map.of("type", "card", "card_id", cardId.toString(),
                      "event_type", eventType, "action", "open_card_details");
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 4);
    }
}
