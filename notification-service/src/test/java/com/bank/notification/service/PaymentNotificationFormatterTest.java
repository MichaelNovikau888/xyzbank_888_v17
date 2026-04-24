package com.bank.notification.service;

import com.bank.notification.event.PaymentCreatedEvent;
import com.bank.notification.event.PaymentStatusChangedEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты PaymentNotificationFormatter.
 */
 /**
 * Покрывает:
 *   1. formatPaymentCreatedPush — полный банковский формат (OPLATA / KARTA / дата / мерчант / баланс)
 *   2. formatPaymentCreatedPush — без cardNumber → «****»
 *   3. formatPaymentCreatedPush — без merchantName → маскированный счёт получателя
 *   4. formatPaymentCreatedPush — без баланса → строка Dostupno не добавляется
 *   5. formatPaymentStatusPush — COMPLETED
 *   6. formatPaymentStatusPush — FAILED с причиной
 *   7. formatPaymentStatusPush — CANCELLED без причины
 *   8. maskCard: различные форматы номеров карт
 *   9. formatAmount: округление до 2 знаков
 *  10. Resilience: null-поля события не бросают исключений
 */
@QuarkusTest
@DisplayName("PaymentNotificationFormatter — unit tests")
class PaymentNotificationFormatterTest {

    @Inject
    PaymentNotificationFormatter formatter;

    // ── 1. Полный банковский формат ───────────────────────────────────────────

    @Test
    @DisplayName("1. formatPaymentCreatedPush — полный формат: все поля заполнены")
    void formatPaymentCreatedPush_allFields_returnsFullBankFormat() {
        PaymentCreatedEvent event = buildEvent(5000.00, "RUB", "40817810099910004312",
                LocalDateTime.of(2026, 4, 16, 18, 12, 57));

        String result = formatter.formatPaymentCreatedPush(
                event,
                "4111111111111234",   // cardNumber
                "Google Play Store",  // merchantName
                new BigDecimal("6000.00")
        );

        assertThat(result).contains("OPLATA 5000.00 RUB");
        assertThat(result).contains("KARTA #1234");
        assertThat(result).contains("16.04.2026 18:12:57");
        assertThat(result).contains("GOOGLE PLAY STORE");
        assertThat(result).contains("Dostupno: 6000.00 RUB");
    }

    // ── 2. Без номера карты → **** ────────────────────────────────────────────

    @Test
    @DisplayName("2. formatPaymentCreatedPush — null cardNumber → KARTA #****")
    void formatPaymentCreatedPush_nullCard_showsMaskedStar() {
        PaymentCreatedEvent event = buildEvent(100.00, "USD", "40817810099910001111", null);

        String result = formatter.formatPaymentCreatedPush(event, null, "Amazon", null);

        assertThat(result).contains("KARTA #****");
    }

    // ── 3. Без merchantName → маскированный счёт ─────────────────────────────

    @Test
    @DisplayName("3. formatPaymentCreatedPush — null merchantName → последние 4 цифры счёта")
    void formatPaymentCreatedPush_nullMerchant_showsMaskedAccount() {
        PaymentCreatedEvent event = buildEvent(200.00, "RUB", "40817810099910004312", null);

        String result = formatter.formatPaymentCreatedPush(event, "4111111111111234", null, null);

        assertThat(result).contains("****4312");
    }

    // ── 4. Без баланса → нет строки Dostupno ─────────────────────────────────

    @Test
    @DisplayName("4. formatPaymentCreatedPush — null balance → нет строки Dostupno")
    void formatPaymentCreatedPush_nullBalance_noBalanceLine() {
        PaymentCreatedEvent event = buildEvent(300.00, "EUR", "40817810099910001234", null);

        String result = formatter.formatPaymentCreatedPush(event, null, "Netflix", null);

        assertThat(result).doesNotContain("Dostupno");
    }

    // ── 5. Статус COMPLETED ───────────────────────────────────────────────────

    @Test
    @DisplayName("5. formatPaymentStatusPush — COMPLETED содержит сумму и валюту")
    void formatPaymentStatusPush_completed_containsAmountAndCurrency() {
        PaymentStatusChangedEvent event = buildStatusEvent("COMPLETED", null);

        String result = formatter.formatPaymentStatusPush(event, null);

        assertThat(result).contains("✅");
        assertThat(result).contains("1500.00");
        assertThat(result).contains("RUB");
    }

    // ── 6. Статус FAILED с причиной ──────────────────────────────────────────

    @Test
    @DisplayName("6. formatPaymentStatusPush — FAILED с reason содержит причину")
    void formatPaymentStatusPush_failed_containsReason() {
        PaymentStatusChangedEvent event = buildStatusEvent("FAILED", "Insufficient funds");

        String result = formatter.formatPaymentStatusPush(event, "4111111111111234");

        assertThat(result).contains("❌");
        assertThat(result).contains("1234");   // маскированная карта
        assertThat(result).contains("Insufficient funds");
    }

    // ── 7. Статус CANCELLED без причины ──────────────────────────────────────

    @Test
    @DisplayName("7. formatPaymentStatusPush — CANCELLED без reason не содержит двоеточия")
    void formatPaymentStatusPush_cancelledNoReason_noColon() {
        PaymentStatusChangedEvent event = buildStatusEvent("CANCELLED", null);

        String result = formatter.formatPaymentStatusPush(event, null);

        assertThat(result).contains("🚫");
        assertThat(result).doesNotContain(": null");
    }

    // ── 8. maskCard ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("8a. maskCard: 16-значный номер → последние 4 цифры")
    void maskCard_16digits_returnsLast4() {
        assertThat(formatter.maskCard("4111111111111234")).isEqualTo("1234");
    }

    @Test
    @DisplayName("8b. maskCard: номер с пробелами → последние 4 цифры")
    void maskCard_withSpaces_returnsLast4() {
        assertThat(formatter.maskCard("4111 1111 1111 5678")).isEqualTo("5678");
    }

    @Test
    @DisplayName("8c. maskCard: null → ****")
    void maskCard_null_returnsStar() {
        assertThat(formatter.maskCard(null)).isEqualTo("****");
    }

    @Test
    @DisplayName("8d. maskCard: пустая строка → ****")
    void maskCard_blank_returnsStar() {
        assertThat(formatter.maskCard("")).isEqualTo("****");
    }

    // ── 9. formatAmount ──────────────────────────────────────────────────────

    @Test
    @DisplayName("9a. formatAmount: целая сумма → два знака после запятой")
    void formatAmount_integer_twoDecimalPlaces() {
        assertThat(formatter.formatAmount(new BigDecimal("5000"))).isEqualTo("5000.00");
    }

    @Test
    @DisplayName("9b. formatAmount: сумма с длинной дробью → округление до 2 знаков")
    void formatAmount_longDecimal_roundedTo2() {
        assertThat(formatter.formatAmount(new BigDecimal("99.999"))).isEqualTo("100.00");
    }

    @Test
    @DisplayName("9c. formatAmount: null → 0.00")
    void formatAmount_null_returnsZero() {
        assertThat(formatter.formatAmount(null)).isEqualTo("0.00");
    }

    // ── 10. Resilience: null-поля не бросают исключений ──────────────────────

    @Test
    @DisplayName("10. Событие с null-полями → fallback, не исключение")
    void formatPaymentCreatedPush_nullEventFields_returnsFallback() {
        PaymentCreatedEvent event = new PaymentCreatedEvent();
        event.paymentId = 1L;
        event.currency  = "RUB";
        // amount, recipientAccount, createdAt — null

        String result = formatter.formatPaymentCreatedPush(event, null, null, null);

        assertThat(result).isNotNull().isNotBlank();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PaymentCreatedEvent buildEvent(double amount, String currency,
                                            String recipientAccount, LocalDateTime createdAt) {
        PaymentCreatedEvent e = new PaymentCreatedEvent();
        e.paymentId        = 42L;
        e.clientId         = "client-1";
        e.amount           = BigDecimal.valueOf(amount);
        e.currency         = currency;
        e.recipientAccount = recipientAccount;
        e.createdAt        = createdAt != null ? createdAt : LocalDateTime.now();
        e.status           = "CREATED";
        return e;
    }

    private PaymentStatusChangedEvent buildStatusEvent(String newStatus, String reason) {
        PaymentStatusChangedEvent e = new PaymentStatusChangedEvent();
        e.paymentId        = 42L;
        e.clientId         = "client-1";
        e.amount           = new BigDecimal("1500.00");
        e.currency         = "RUB";
        e.recipientAccount = "40817810099910004312";
        e.newStatus        = newStatus;
        e.oldStatus        = "CREATED";
        e.reason           = reason;
        return e;
    }
}
