package com.bank.transfer.notification;

import com.bank.transfer.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Единое событие уведомления о переводе.
 */
 /**
 * Публикуется transfer-service в топик: transfer.notification
 * Потребитель: notification-service (TransferNotificationConsumer)
 */
 /**
 * Используется для двух типов событий:
 *   CREATED   — перевод создан (push «Перевод создан на сумму X»)
 *   REVIEW    — перевод на ручной проверке антифрода (только push, не финальный)
 *   COMPLETED — перевод одобрен антифродом (push + подробный email)
 *   BLOCKED   — перевод заблокирован антифродом (push + email с причиной)
 *   CANCELLED — перевод отменён (push + email)
 */
 /**
 * Единый класс вместо двух (TransferCreated + TransferStatusChanged) — чтобы
 * notification-service получал все нужные поля с самого начала и не делал
 * дополнительных запросов. Опциональные поля (reason, recipientDisplay) null
 * для CREATED.
 */
 /**
 * Поле transferType: ACCOUNT / CARD / PHONE — для отображения «куда» в email.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferNotificationEvent {

    // ── Идентификация ────────────────────────────────────────────────────────

    /** ID перевода в transfer-service. */
    private Long           transferId;

    /** Идентификатор клиента (для lookup email и push-токена). */
    private String         clientId;

    /** ACCOUNT / CARD / PHONE. */
    private String         transferType;

    // ── Статус ───────────────────────────────────────────────────────────────

    /** Текущий статус: CREATED / REVIEW / COMPLETED / BLOCKED / CANCELLED. */
    private TransferStatus status;

 /**
     * Причина блокировки (BLOCKED) или отмены (CANCELLED).
     * Null для CREATED и COMPLETED.
 */
    private String         reason;

    // ── Детали перевода (для email) ───────────────────────────────────────────

    /** Сумма перевода. */
    private BigDecimal     amount;

    /** Валюта (RUB / USD / EUR). */
    private String         currency;

 /**
     * Получатель в человекочитаемом виде:
     *   ACCOUNT → номер счёта (accountNumber)
     *   CARD    → маскированный номер карты (**** **** **** 1234)
     *   PHONE   → номер телефона (+7 *** *** ** **)
 */
    private String         recipientDisplay;

    /** Назначение платежа. */
    private String         purpose;

    /** Время события (CREATED — время создания; COMPLETED/BLOCKED — время решения). */
    private LocalDateTime  occurredAt;
}
