package com.bank.notification.service;

import com.bank.notification.event.PaymentCreatedEvent;
import com.bank.notification.event.PaymentStatusChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Форматировщик push-уведомлений о платежах в стиле реальных банков.
 */
 /**
 * <p>Формат (по образцу Беларусьбанка):
 * <pre>
 *   OPLATA 5000.00 RUB
 *   KARTA #2859
 *   16.04.2026 18:12:57
 *   GOOGLE PLAY STORE >MOSCOW>RU
 *   Dostupno: 6000.00 RUB
 * </pre>
 */
 /**
 * <p>Если детали получателя или карты недоступны — используются
 * безопасные fallback-значения (метод никогда не бросает исключений).
 */
@ApplicationScoped
public class PaymentNotificationFormatter {

    private static final Logger LOG = Logger.getLogger(PaymentNotificationFormatter.class);

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    // ── Public API ────────────────────────────────────────────────────────────

 /**
     * Формирует push-текст для события «платёж создан» в банковском формате.
 */
 /**
     * @param event        событие создания платежа
     * @param cardNumber   полный номер карты (маскируется до последних 4 цифр); null → «****»
     * @param merchantName название мерчанта в верхнем регистре; null → recipientAccount
     * @param balance      текущий баланс счёта после списания; null → строка не добавляется
     * @return отформатированная строка для push-уведомления
 */
    public String formatPaymentCreatedPush(PaymentCreatedEvent event,
                                            String cardNumber,
                                            String merchantName,
                                            BigDecimal balance) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("OPLATA ").append(formatAmount(event.amount))
              .append(" ").append(event.currency).append("\n");
            sb.append("KARTA #").append(maskCard(cardNumber)).append("\n");
            sb.append(formatDateTime(event.createdAt)).append("\n");
            sb.append(formatMerchant(merchantName, event.recipientAccount));
            if (balance != null) {
                sb.append("\nDostupno: ")
                  .append(formatAmount(balance))
                  .append(" ").append(event.currency);
            }
            return sb.toString();
        } catch (Exception e) {
            LOG.warnf(e, "formatPaymentCreatedPush failed for paymentId=%d, using fallback",
                      event.paymentId);
            return fallbackCreatedPush(event);
        }
    }

 /**
     * Формирует push-текст для события смены статуса платежа.
 */
 /**
     * @param event      событие смены статуса
     * @param cardNumber полный номер карты (маскируется); null → «****»
     * @return отформатированная строка для push-уведомления
 */
    public String formatPaymentStatusPush(PaymentStatusChangedEvent event,
                                           String cardNumber) {
        try {
            return switch (event.newStatus) {
                case "COMPLETED" -> String.format("✅ Платёж №%d выполнен: %s %s",
                        event.paymentId, formatAmount(event.amount), event.currency);

                case "FAILED"    -> String.format("❌ Платёж №%d отклонён: KARTA #%s%s",
                        event.paymentId, maskCard(cardNumber),
                        event.reason != null ? "\n" + event.reason : "");

                case "CANCELLED" -> String.format("🚫 Платёж №%d отменён%s",
                        event.paymentId,
                        event.reason != null ? ": " + event.reason : "");

                case "PROCESSING" -> String.format("⏳ Платёж №%d обрабатывается",
                        event.paymentId);

                default -> String.format("Платёж №%d: статус изменён на %s",
                        event.paymentId, event.newStatus);
            };
        } catch (Exception e) {
            LOG.warnf(e, "formatPaymentStatusPush failed for paymentId=%d", event.paymentId);
            return "Статус платежа изменён";
        }
    }

    // ── Package-private helpers (доступны для тестирования) ──────────────────

 /**
     * Маскирует номер карты — оставляет последние 4 цифры.
     * Пример: «4111111111111234» → «1234»
 */
    String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) return "****";
        String digits = cardNumber.replaceAll("\\D", "");
        if (digits.length() < 4) return "****";
        return digits.substring(digits.length() - 4);
    }

 /**
     * Форматирует сумму: ровно 2 знака после запятой, без разделителей тысяч.
     * Пример: 5000 → «5000.00»
 */
    String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

 /**
     * Форматирует дату-время: «16.04.2026 18:12:57».
 */
    String formatDateTime(LocalDateTime dt) {
        if (dt == null) return LocalDateTime.now().format(DATE_TIME_FMT);
        return dt.format(DATE_TIME_FMT);
    }

 /**
     * Форматирует строку мерчанта.
     * Если merchantName задан — «GOOGLE PLAY STORE»,
     * иначе — маскированный номер счёта получателя.
 */
    String formatMerchant(String merchantName, String recipientAccount) {
        if (merchantName != null && !merchantName.isBlank()) {
            return merchantName.toUpperCase();
        }
        if (recipientAccount != null && recipientAccount.length() >= 4) {
            return "****" + recipientAccount.substring(recipientAccount.length() - 4);
        }
        return "****";
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String fallbackCreatedPush(PaymentCreatedEvent event) {
        return String.format("OPLATA %s %s",
                event.amount != null ? formatAmount(event.amount) : "?.??",
                event.currency != null ? event.currency : "");
    }
}
