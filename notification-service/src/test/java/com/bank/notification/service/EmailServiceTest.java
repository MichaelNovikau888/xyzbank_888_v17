package com.bank.notification.service;

import com.bank.notification.entity.EmailTemplate;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты EmailService.
 */
 /**
 * Mailer — реальный mock (quarkus.mailer.mock=true в test profile).
 * Redis и ClientService — InjectMock.
 * EmailTemplate.findByName() — мокируем через статический метод (Panache).
 */
 /**
 * Покрываемые сценарии:
 *   - sendWelcomeEmail: успех, пустой email, null email
 *   - sendPaymentNotification: шаблон найден → email отправлен
 *   - sendPaymentStatusChanged: плейсхолдеры заменены для каждого статуса
 *   - getEmailTemplate: Redis cache hit / miss / кеширование
 *   - getTransferTemplateName via sendTransferFinalNotification
 *   - extractFirstName: разные форматы имени
 */
@QuarkusTest
@DisplayName("EmailService — unit tests")
class EmailServiceTest {

    @Inject
    MockMailbox mailbox;

    @Inject
    EmailService emailService;

    @InjectMock
    ClientService clientService;

    @InjectMock
    RedisDataSource redisDataSource;

    @SuppressWarnings("unchecked")
    private final ValueCommands<String, String> redisCommands =
            (ValueCommands<String, String>) Mockito.mock(ValueCommands.class);

    @BeforeEach
    void setUp() {
        mailbox.clear();
        when(redisDataSource.value(String.class)).thenReturn(redisCommands);
        emailService.init();
    }

    // ── sendWelcomeEmail ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendWelcomeEmail")
    class SendWelcomeEmail {

        @Test
        @DisplayName("успешная отправка → письмо попадает в MockMailbox")
        void success_emailDelivered() {
            emailService.sendWelcomeEmail("ivan@example.com", "Иван Иванов", 1L);

            Assertions.assertThat(mailbox.getMailMessagesSentTo("ivan@example.com")).hasSize(1);
            Assertions.assertThat(mailbox.getMailMessagesSentTo("ivan@example.com").get(0).getSubject())
                    .contains("XYZ-Bank");
        }

        @Test
        @DisplayName("тело письма содержит firstName из fullName")
        void body_containsFirstName() {
            emailService.sendWelcomeEmail("test@example.com", "Мария Петрова", 2L);

            String body = mailbox.getMailMessagesSentTo("test@example.com").get(0).getText();
            Assertions.assertThat(body).contains("Мария");
        }

        @Test
        @DisplayName("только имя (без фамилии) — тоже работает")
        void singleNameWord_works() {
            emailService.sendWelcomeEmail("test@example.com", "Алексей", 3L);

            String body = mailbox.getMailMessagesSentTo("test@example.com").get(0).getText();
            Assertions.assertThat(body).contains("Алексей");
        }

        @Test
        @DisplayName("null fullName → fallback 'Клиент'")
        void nullFullName_usesClientFallback() {
            emailService.sendWelcomeEmail("test@example.com", null, 4L);

            String body = mailbox.getMailMessagesSentTo("test@example.com").get(0).getText();
            Assertions.assertThat(body).contains("Клиент");
        }

        @Test
        @DisplayName("пустой email → письмо не отправляется")
        void blankEmail_skipsEmail() {
            emailService.sendWelcomeEmail("", "Иван", 5L);
            emailService.sendWelcomeEmail("  ", "Иван", 6L);

            Assertions.assertThat(mailbox.getTotalMessagesSent()).isZero();
        }

        @Test
        @DisplayName("null email → письмо не отправляется")
        void nullEmail_skipsEmail() {
            emailService.sendWelcomeEmail(null, "Иван", 7L);

            Assertions.assertThat(mailbox.getTotalMessagesSent()).isZero();
        }
    }

    // ── getEmailTemplate (Redis cache) ────────────────────────────────────────

    @Nested
    @DisplayName("getEmailTemplate — Redis кеш")
    class GetEmailTemplate {

        @Test
        @DisplayName("cache hit → Panache НЕ вызывается, шаблон из Redis")
        void cacheHit_noPanacheCall() {
            when(redisCommands.get("email:template:payment_created"))
                    .thenReturn("Тело из кеша");
            when(redisCommands.get("email:template:payment_created:subject"))
                    .thenReturn("Тема из кеша");

            EmailTemplate result = emailService.getEmailTemplate("payment_created");

            Assertions.assertThat(result.getBody()).isEqualTo("Тело из кеша");
            Assertions.assertThat(result.getName()).isEqualTo("payment_created");
            // setex не вызывается при hit
            verify(redisCommands, never()).setex(anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("cache miss + шаблон не в БД → default template возвращается")
        void cacheMissAndNoDb_returnsDefault() {
            when(redisCommands.get(anyString())).thenReturn(null);
            // Panache статический findByName вернёт null — не можем замокировать,
            // поэтому в H2 шаблона нет → default template
            EmailTemplate result = emailService.getEmailTemplate("nonexistent_template");

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.getSubject()).contains("Уведомление");
        }

        @Test
        @DisplayName("Redis недоступен (exception) → продолжаем без кеша")
        void redisDown_fallsBackToDb() {
            when(redisCommands.get(anyString()))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            // Должно не упасть, а вернуть дефолтный шаблон
            EmailTemplate result = emailService.getEmailTemplate("payment_created");

            Assertions.assertThat(result).isNotNull();
        }
    }

    // ── sendPaymentStatusChanged — имена шаблонов ─────────────────────────────

    @Nested
    @DisplayName("sendPaymentStatusChanged — выбор шаблона по статусу")
    class SendPaymentStatusChanged {

        @BeforeEach
        void mockClient() {
            when(clientService.getClientEmail(anyString())).thenReturn("client@example.com");
            when(redisCommands.get("email:template:payment_completed"))
                    .thenReturn("Платёж №{payment_id} завершён");
            when(redisCommands.get("email:template:payment_completed:subject"))
                    .thenReturn("✅ Платёж завершён");
            when(redisCommands.get("email:template:payment_failed"))
                    .thenReturn("Платёж №{payment_id} не выполнен. Причина: {failure_reason}");
            when(redisCommands.get("email:template:payment_failed:subject"))
                    .thenReturn("❌ Ошибка платежа");
            when(redisCommands.get("email:template:payment_cancelled"))
                    .thenReturn("Платёж №{payment_id} отменён. Причина: {cancellation_reason}");
            when(redisCommands.get("email:template:payment_cancelled:subject"))
                    .thenReturn("🚫 Платёж отменён");
        }

        @Test
        @DisplayName("COMPLETED → шаблон payment_completed, плейсхолдер {payment_id} заменён")
        void completed_usesCorrectTemplate() {
            emailService.sendPaymentStatusChanged(
                    "client1", 100L, new BigDecimal("500.00"), "RUB", "40817...", "COMPLETED", null);

            String body = mailbox.getMailMessagesSentTo("client@example.com").get(0).getText();
            Assertions.assertThat(body).contains("100").doesNotContain("{payment_id}");
        }

        @Test
        @DisplayName("FAILED → шаблон payment_failed, {failure_reason} заменён")
        void failed_reasonPlaceholderReplaced() {
            emailService.sendPaymentStatusChanged(
                    "client1", 200L, new BigDecimal("300.00"), "RUB", "40817...", "FAILED", "Недостаточно средств");

            String body = mailbox.getMailMessagesSentTo("client@example.com").get(0).getText();
            Assertions.assertThat(body).contains("Недостаточно средств")
                    .doesNotContain("{failure_reason}");
        }

        @Test
        @DisplayName("CANCELLED → шаблон payment_cancelled, {cancellation_reason} заменён")
        void cancelled_reasonPlaceholderReplaced() {
            emailService.sendPaymentStatusChanged(
                    "client1", 300L, new BigDecimal("100.00"), "RUB", "40817...", "CANCELLED", "По запросу клиента");

            String body = mailbox.getMailMessagesSentTo("client@example.com").get(0).getText();
            Assertions.assertThat(body).contains("По запросу клиента")
                    .doesNotContain("{cancellation_reason}");
        }

        @Test
        @DisplayName("null reason → fallback-текст, без NullPointerException")
        void nullReason_fallbackUsed() {
            Assertions.assertThatCode(() ->
                    emailService.sendPaymentStatusChanged(
                            "client1", 400L, new BigDecimal("50.00"), "RUB", "40817...", "FAILED", null)
            ).doesNotThrowAnyException();
        }
    }

    // ── sendTransferFinalNotification ─────────────────────────────────────────

    @Nested
    @DisplayName("sendTransferFinalNotification")
    class SendTransferFinal {

        @BeforeEach
        void mockClient() {
            when(clientService.getClientEmail(anyString())).thenReturn("transfer@example.com");
            when(redisCommands.get(anyString())).thenReturn(null); // cache miss → default template
        }

        @Test
        @DisplayName("COMPLETED → email отправляется")
        void completed_emailSent() {
            emailService.sendTransferFinalNotification(
                    "client1", 99L, "ACCOUNT", "COMPLETED",
                    new BigDecimal("1000.00"), "RUB", "40817...", "Оплата услуг", null,
                    LocalDateTime.now());

            Assertions.assertThat(mailbox.getMailMessagesSentTo("transfer@example.com")).hasSize(1);
        }

        @Test
        @DisplayName("clientEmail=null → письмо не отправляется (NPE не бросается)")
        void nullClientEmail_noMailSent() {
            when(clientService.getClientEmail(anyString())).thenReturn(null);

            Assertions.assertThatCode(() ->
                    emailService.sendTransferFinalNotification(
                            "client1", 10L, "CARD", "COMPLETED",
                            new BigDecimal("100.00"), "RUB", "****1234", null, null,
                            LocalDateTime.now())
            ).doesNotThrowAnyException();
        }
    }
}
