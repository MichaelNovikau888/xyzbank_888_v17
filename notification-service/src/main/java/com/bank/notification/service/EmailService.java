package com.bank.notification.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import com.bank.notification.entity.EmailTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Email-сервис с Redis-кешированием шаблонов (TTL = 1 час).
 */
 /**
 * Поддерживает два домена:
 *   - Платежи (payment_*): существующие методы
 *   - Переводы (transfer_*): новые методы для transfer-service
 */
@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final Duration TEMPLATE_TTL = Duration.ofHours(1);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Inject Mailer          mailer;
    @Inject RedisDataSource redisDataSource;
    @Inject ClientService   clientService;

    private ValueCommands<String, String> redisCommands;

    @jakarta.annotation.PostConstruct
    void init() {
        redisCommands = redisDataSource.value(String.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ПЛАТЕЖИ (payment-api)
    // ══════════════════════════════════════════════════════════════════════════

    /** Email при создании платежа (CREATED). */
    public void sendPaymentNotification(String clientId, Long paymentId,
                                        BigDecimal amount, String currency,
                                        String recipientAccount) {
        String clientEmail = clientService.getClientEmail(clientId);
        EmailTemplate template = getEmailTemplate("payment_created");

        String body = template.getBody()
                .replace("{payment_id}",        paymentId.toString())
                .replace("{amount}",            amount.toString())
                .replace("{currency}",          currency)
                .replace("{recipient_account}", recipientAccount);

        sendEmail(clientEmail, template.getSubject(), body);
        LOG.infof("Email (PAYMENT CREATED) sent to %s for payment_id=%d", clientEmail, paymentId);
    }

    /** Email при изменении статуса платежа. */
    public void sendPaymentStatusChanged(String clientId, Long paymentId,
                                         BigDecimal amount, String currency,
                                         String recipientAccount, String newStatus,
                                         String reason) {
        String clientEmail = clientService.getClientEmail(clientId);
        EmailTemplate template = getEmailTemplate(getPaymentTemplateName(newStatus));

        String body = template.getBody()
                .replace("{payment_id}",          paymentId.toString())
                .replace("{amount}",              amount.toString())
                .replace("{currency}",            currency)
                .replace("{recipient_account}",   recipientAccount)
                .replace("{completion_date}",     LocalDateTime.now().format(DATE_FORMATTER))
                .replace("{cancellation_date}",   LocalDateTime.now().format(DATE_FORMATTER))
                .replace("{failure_reason}",      reason != null ? reason : "Техническая ошибка")
                .replace("{cancellation_reason}", reason != null ? reason : "По запросу клиента");

        sendEmail(clientEmail, template.getSubject(), body);
        LOG.infof("Email (%s) sent to %s for payment_id=%d", newStatus, clientEmail, paymentId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ПЕРЕВОДЫ (transfer-service) — НОВЫЕ МЕТОДЫ
    // ══════════════════════════════════════════════════════════════════════════

 /**
     * Подробный email о финальном статусе перевода:
     * COMPLETED, BLOCKED, CANCELLED.
 */
 /**
     * Содержит: сумму, получателя, тип перевода, назначение, дату, причину (если есть).
 */
    public void sendTransferFinalNotification(String clientId,
                                               Long transferId,
                                               String transferType,
                                               String status,
                                               BigDecimal amount,
                                               String currency,
                                               String recipientDisplay,
                                               String purpose,
                                               String reason,
                                               LocalDateTime occurredAt) {
        String clientEmail = clientService.getClientEmail(clientId);
        String templateName = getTransferTemplateName(status);
        EmailTemplate template = getEmailTemplate(templateName);

        String dateStr = occurredAt != null
                ? occurredAt.format(DATE_FORMATTER)
                : LocalDateTime.now().format(DATE_FORMATTER);

        String body = template.getBody()
                .replace("{transfer_id}",       transferId.toString())
                .replace("{transfer_type}",     translateTransferType(transferType))
                .replace("{amount}",            amount.toString())
                .replace("{currency}",          currency != null ? currency : "RUB")
                .replace("{recipient_display}", recipientDisplay != null ? recipientDisplay : "—")
                .replace("{purpose}",           purpose != null && !purpose.isBlank() ? purpose : "Не указано")
                .replace("{occurred_date}",     dateStr)
                .replace("{block_reason}",      reason != null ? reason : "Превышен порог безопасности")
                .replace("{cancel_reason}",     reason != null ? reason : "По запросу клиента");

        sendEmail(clientEmail, template.getSubject(), body);
        LOG.infof("Email (TRANSFER %s) sent to %s for transfer_id=%d",
                  status, clientEmail, transferId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Шаблоны с Redis-кешированием
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public EmailTemplate getEmailTemplate(String templateName) {
        String cacheKey = "email:template:" + templateName;
        String cachedBody = redisCommands.get(cacheKey);

        if (cachedBody != null) {
            LOG.debugf("Template '%s' found in Redis cache", templateName);
            EmailTemplate t = new EmailTemplate();
            t.setName(templateName);
            t.setSubject(getSubjectFromCache(templateName));
            t.setBody(cachedBody);
            return t;
        }

        LOG.debugf("Cache miss for template '%s', fetching from PostgreSQL", templateName);
        EmailTemplate template = EmailTemplate.findByName(templateName);

        if (template == null) {
            LOG.warnf("Template '%s' not found in database, using default", templateName);
            return createDefaultTemplate(templateName);
        }

        redisCommands.setex(cacheKey, TEMPLATE_TTL.getSeconds(), template.getBody());
        redisCommands.setex(cacheKey + ":subject", TEMPLATE_TTL.getSeconds(), template.getSubject());
        LOG.debugf("Template '%s' cached in Redis", templateName);

        return template;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendEmail(String to, String subject, String body) {
        try {
            mailer.send(
                    Mail.withText(to, subject, body)
                            .setFrom("noreply@examplebank.ru")
            );
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send email to %s", to);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private String getPaymentTemplateName(String status) {
        return switch (status) {
            case "CREATED"    -> "payment_created";
            case "PROCESSING" -> "payment_processing";
            case "COMPLETED"  -> "payment_completed";
            case "FAILED"     -> "payment_failed";
            case "CANCELLED"  -> "payment_cancelled";
            default -> {
                LOG.warnf("Unknown payment status '%s', falling back to payment_created", status);
                yield "payment_created";
            }
        };
    }

    private String getTransferTemplateName(String status) {
        return switch (status) {
            case "COMPLETED"  -> "transfer_completed";
            case "BLOCKED"    -> "transfer_blocked";
            case "CANCELLED"  -> "transfer_cancelled";
            default -> {
                LOG.warnf("Unknown transfer status '%s', falling back to transfer_completed", status);
                yield "transfer_completed";
            }
        };
    }

    private String translateTransferType(String type) {
        if (type == null) return "Перевод";
        return switch (type) {
            case "ACCOUNT" -> "Перевод на счёт";
            case "CARD"    -> "Перевод на карту";
            case "PHONE"   -> "Перевод по номеру телефона";
            default        -> "Перевод";
        };
    }


    // ══════════════════════════════════════════════════════════════════════════
    // РЕГИСТРАЦИЯ (authorization-service)
    // ══════════════════════════════════════════════════════════════════════════

 /**
     * Welcome email при регистрации нового клиента.
 */
 /**
     * <p>Отправляется на email клиента сразу после успешной регистрации.
     * Вызывается из {@code RegistrationNotificationConsumer}.
 */
 /**
     * @param email    адрес клиента
     * @param fullName полное имя клиента
     * @param userId   ID пользователя в authorization-service
 */
    public void sendWelcomeEmail(String email, String fullName, Long userId) {
        if (email == null || email.isBlank()) {
            LOG.warnf("sendWelcomeEmail: email is blank for userId=%d, skipping", userId);
            return;
        }

        String firstName = extractFirstName(fullName);

        String subject = "Добро пожаловать в XYZ-Bank!";
        String body = String.format("""
                Уважаемый(ая) %s!

                Вы успешно зарегистрировались в XYZ-Bank.

                Ваш аккаунт создан и в ближайшее время вы получите доступ
                к банковским услугам.

                Если вы не регистрировались в XYZ-Bank, немедленно
                свяжитесь с нами: support@xyzbank.com или +7 800 000-00-00.

                С уважением,
                XYZ-Bank
                """,
                firstName);

        try {
            mailer.send(Mail.withText(email, subject, body));
            LOG.infof("Welcome email sent to %s (userId=%d)", email, userId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send welcome email to %s (userId=%d)", email, userId);
            throw e; // пробрасываем — consumer обработает через DLQ
        }
    }

    /** Извлекает первое слово из полного имени. */
    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Клиент";
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private String getSubjectFromCache(String templateName) {
        String cached = redisCommands.get("email:template:" + templateName + ":subject");
        return cached != null ? cached : "Уведомление - Example Bank";
    }

    private EmailTemplate createDefaultTemplate(String templateName) {
        EmailTemplate t = new EmailTemplate();
        t.setName(templateName);
        t.setSubject("Уведомление - Example Bank");
        t.setBody("""
                Уважаемый клиент!
                
                Информация о вашем переводе №{transfer_id} на сумму {amount} {currency}.
                
                С уважением,
                Example Bank
                Служба поддержки: support@examplebank.ru
                """);
        return t;
    }
}
