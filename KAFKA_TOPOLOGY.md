# KAFKA_TOPOLOGY.md — XYZ-Bank v5
> Актуально на момент анализа кода. Обновлять при каждом изменении топиков.

---

## Условные обозначения
- ✅ **Живой** — producer и consumer найдены, поток работает
- 🔴 **Мёртвый** — есть producer или consumer, но не оба
- ⚠️ **Риск** — работает, но есть архитектурная проблема
- 🏚️ **Балласт** — топик объявлен, но не используется вообще

---

## 1. Живые end-to-end потоки

### 1.1 Платёж (payment-api)
```
POST /api/v1/payments
  └─▶ [outbox]
        ├─▶ payment.created          ──▶ notification-service  (push + email)   ✅
        ├─▶ payment.created          ──▶ report-service        (CSV-отчёт)      ✅
        └─▶ payment.antifraud.check  ──▶ antifraud

antifraud
  └─▶ payment.antifraud.response  ──▶ payment-api (AntifraudResponseConsumer)
        └─▶ [outbox]
              ├─▶ payment.status.changed  ──▶ notification-service  ✅
              └─▶ account.events          ──▶ history-service        ✅
```

### 1.2 Перевод (transfer)
```
POST /api/v1/transfers/{type}
  └─▶ TransferServiceImpl.save*Transfer()
        └─▶ [outbox — три события в одной транзакции]
              ├─▶ suspicious-transfers.create  ──▶ (устарел, см. §3)         🔴
              ├─▶ transfer.antifraud.check      ──▶ antifraud                 ✅
              └─▶ transfer.notification (CREATED) ──▶ notification-service    ✅

antifraud
  └─▶ transfer.antifraud.response  ──▶ transfer (AntifraudResponseConsumer)
        └─▶ [outbox]
              ├─▶ transfer.notification (COMPLETED/BLOCKED)  ──▶ notification  ✅
              └─▶ transfer.events                             ──▶ history       ✅
```

### 1.3 CRUD аккаунтов (account ↔ authorization)
```
[внешний клиент / authorization]
  └─▶ account.create / account.update / account.delete / account.get / account.getById
        └─▶ account-service (AccountCommandConsumer / AccountQueryConsumer)
              └─▶ external.account.create / .update / .delete / .get / .getById
                    └─▶ authorization (ответы)                                ✅

account-service → auth.validate ──▶ authorization
                              └─▶ auth.validate.response ──▶ account-service ✅

account-service → audit.logs ──▶ history-service                             ✅
account-service → error.logs ──▶ history-service                             ✅
```

### 1.4 Аудит и история
```
antifraud    ──▶ audit.logs  ──▶ history-service  ✅
profile      ──▶ audit.logs  ──▶ history-service  ✅
account      ──▶ audit.logs  ──▶ history-service  ✅

payment-api  ──▶ account.events  ──▶ history-service  ✅
transfer     ──▶ transfer.events ──▶ history-service  ✅
profile      ──▶ error.logs      ──▶ history-service  ✅
public-info  ──▶ public-info.error-logs (сам себе)    ⚠️
```

---

## 2. Полная матрица топиков

### payment-api
| Топик | Направление | Consumer | Статус |
|---|---|---|---|
| `payment.created` | OUT (outbox) | notification-service, report-service | ✅ |
| `payment.status.changed` | OUT (direct) | notification-service | ✅ |
| `payment.antifraud.check` | OUT (outbox) | antifraud | ✅ |
| `payment.antifraud.response` | IN | ← antifraud | ✅ |
| `account.events` | OUT (outbox) | history-service | ✅ |
| `payment.created.dlq` | OUT (авто DLQ) | никто | ⚠️ |

### transfer
| Топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `transfer.antifraud.check` | OUT (outbox) | antifraud | ✅ |
| `transfer.antifraud.response` | IN | ← antifraud | ✅ |
| `transfer.events` | OUT (outbox) | history-service | ✅ |
| `transfer.notification` | OUT (outbox) | notification-service | ✅ |
| `transfer.account` | OUT (AuditAspect→TransferProducer) | TransferConsumer + AuditConsumer (внутри!) | ⚠️ |
| `transfer.card` | OUT (AuditAspect→TransferProducer) | TransferConsumer + AuditConsumer (внутри!) | ⚠️ |
| `transfer.phone` | OUT (AuditAspect→TransferProducer) | TransferConsumer + AuditConsumer (внутри!) | ⚠️ |
| `suspicious-transfers.create` | OUT (outbox + TransferProducer) | никто не слушает через @KafkaListener | 🔴 |
| `audit.history` | OUT (TransferProducer.sendAuditHistory) | никто не слушает | 🔴 |
| `error.logging` | OUT (KafkaErrorPublisher) | никто не слушает | 🔴 |
| `topicAccountDetailsGet` | IN | никто не продюсирует | 🔴 |

### antifraud
| Топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `payment.antifraud.check` | IN | ← payment-api | ✅ |
| `payment.antifraud.response` | OUT | payment-api | ✅ |
| `transfer.antifraud.check` | IN | ← transfer | ✅ |
| `transfer.antifraud.response` | OUT | transfer | ✅ |
| `audit.logs` | OUT | history-service | ✅ |
| `suspicious-transfers.create` | OUT (SuspiciousTransferProducer) | никто через @KafkaListener | 🔴 |
| `suspicious-transfers.update` | OUT | никто | 🏚️ |
| `suspicious-transfers.delete` | OUT | никто | 🏚️ |
| `suspicious-transfers.get` | OUT | никто | 🏚️ |
| `suspicious-transfers.Response` | OUT | никто + **опечатка** (R заглавная) | 🏚️ |

### account-service
| Топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `account.create` | IN | ← authorization / внешний | ✅ |
| `account.update` | IN | ← authorization / внешний | ✅ |
| `account.delete` | IN | ← authorization / внешний | ✅ |
| `account.get` | IN | ← authorization / внешний | ✅ |
| `account.getById` | IN | ← authorization / внешний | ✅ |
| `external.account.create` | OUT | authorization | ✅ |
| `external.account.update` | OUT | authorization | ✅ |
| `external.account.delete` | OUT | authorization | ✅ |
| `external.account.get` | OUT | authorization | ✅ |
| `external.account.getById` | OUT | authorization | ✅ |
| `auth.validate` | OUT | authorization | ✅ |
| `auth.validate.response` | IN | ← authorization | ✅ |
| `audit.logs` | IN (AuditConsumer) + OUT | history-service / antifraud | ✅ |
| `error.logs` | OUT | history-service | ✅ |
| `external.audit.logs` | OUT (AuditProducer) | **никто не слушает** | 🔴 |
| `card.created` | OUT (CardEventProducer) | **никто не слушает** | 🔴 |
| `card.blocked` | OUT | **никто не слушает** | 🔴 |
| `card.unblocked` | OUT | **никто не слушает** | 🔴 |
| `card.limit.changed` | OUT | **никто не слушает** | 🔴 |

### authorization
| Топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `auth.login` | IN | внешний клиент (нет в монорепо) | ⚠️ |
| `auth.login.response` | OUT | внешний клиент | ⚠️ |
| `auth.validate` | IN | ← account-service | ✅ |
| `auth.validate.response` | OUT | account-service | ✅ |
| `user.create` / `.response` | IN/OUT | внешний клиент | ⚠️ |
| `user.update` / `.response` | IN/OUT | внешний клиент | ⚠️ |
| `user.delete` / `.response` | IN/OUT | внешний клиент | ⚠️ |
| `user.get` / `.response` | IN/OUT | внешний клиент | ⚠️ |
| `error.logging` | OUT | **никто не слушает** (нужен `error.logs`!) | 🔴 |

### notification-service
| Топик | Направление | Producer | Статус |
|---|---|---|---|
| `payment.created` | IN | ← payment-api | ✅ |
| `payment.status.changed` | IN | ← payment-api | ✅ |
| `transfer.notification` | IN | ← transfer | ✅ |
| `payment.created.dlq` | IN (DLQ) | SmallRye авто | ⚠️ только лог |
| `transfer.notification.dlq` | IN (DLQ) | SmallRye авто | ⚠️ только лог |

### report-service
| Топик | Направление | Producer | Статус |
|---|---|---|---|
| `payment.created` | IN | ← payment-api | ✅ |
| `payment.status.changed` | **не подписан** | payment-api шлёт | 🔴 неполные отчёты |

### history-service
| Канал | Реальный топик | Producer | Статус |
|---|---|---|---|
| `audit-logs-in` | `audit.logs` | antifraud, account, profile | ✅ |
| `transfer-events-in` | `transfer.events` | transfer (outbox) | ✅ |
| `account-events-in` | `account.events` | payment-api (outbox) | ✅ |
| `error-logs-in` | `error.logs` | profile, public-info | ⚠️ не все шлют |
| `audit-logs-dlq` | `audit-logs-dlq` | SmallRye авто | ⚠️ только лог |
| `error-logs-dlq` | `error-logs-dlq` | SmallRye авто | ⚠️ только лог |

### profile-service
| Реальный топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `profile.create` | IN | **никто не продюсирует** в монорепо | 🔴 |
| `profile.update` | IN | **никто не продюсирует** | 🔴 |
| `profile.delete` | IN | **никто не продюсирует** | 🔴 |
| `profile.get` | IN | **никто не продюсирует** | 🔴 |
| `profile.get-response` | OUT | **никто не слушает** | 🔴 |
| `account.details.create` | IN | **никто не продюсирует** | 🔴 |
| `account.details.update` | IN | **никто не продюсирует** | 🔴 |
| `account.details.delete` | IN | **никто не продюсирует** | 🔴 |
| `account.details.get` | IN | **никто не продюсирует** | 🔴 |
| `account.details.get-response` | OUT | **никто не слушает** | 🔴 |
| `audit.logs` | OUT | history-service | ✅ |
| `error.logs` | OUT | history-service | ✅ |

> **Примечание:** profile-service предполагает внешний API-gateway или admin-сервис
> как источник команд. В рамках монорепо продюсеров нет.

### public-info
| Реальный топик | Направление | Другой конец | Статус |
|---|---|---|---|
| `public-info.bank.create/update/delete` | IN | внешняя admin-система | ⚠️ нет в монорепо |
| `public-info.branch.create/update/delete` | IN | внешняя admin-система | ⚠️ |
| `public-info.atm.create/update/delete` | IN | внешняя admin-система | ⚠️ |
| `public-info.certificate.create/update/delete` | IN | внешняя admin-система | ⚠️ |
| `public-info.license.create/update/delete` | IN | внешняя admin-система | ⚠️ |
| `public-info.error-logs` | IN + OUT | сам себе (ErrorProducer) | ⚠️ замкнут |

---

## 3. Критические проблемы с планом исправления

### 🔴 P0 — Потеря данных / сломанная логика

#### 3.1 `error.logging` ≠ `error.logs` (transfer + authorization)
**Проблема:** transfer (`KafkaErrorPublisher`) и authorization шлют ошибки в топик
`error.logging` (с суффиксом `-ing`), а history-service слушает `error.logs`.
Все ошибки этих двух сервисов **бесследно исчезают**.

**Исправление в двух файлах:**

`transfer/src/main/java/com/bank/transfer/exception/KafkaErrorPublisher.java`
```java
// 🔴 Было:
kafkaTemplate.send("error.logging", response);

// ✅ Стало:
kafkaTemplate.send("error.logs", response);
```

`authorization/src/main/resources/application-local.yaml`
```yaml
# 🔴 Было:
error-logging: error.logging

# ✅ Стало:
error-logging: error.logs
```

---

#### 3.2 `audit.history` — мёртвый топик в TransferProducer
**Проблема:** `TransferProducer.sendAuditHistory()` шлёт в `audit.history`,
но history-service слушает `transfer.events` (через outbox). Метод вызывается
из `AuditAspect` перед `@Before saveAccountTransfer` — это **дублирующий путь**,
и старый при этом мёртвый.

**Исправление:**
`TransferProducer.java` — удалить метод `sendAuditHistory()`.
`AuditAspect.java` — убрать вызовы `transferProducer.send*Transfer()`,
так как `TransferServiceImpl` уже делает всё через outbox.

> AuditAspect сейчас вызывает `sendAccountTransfer/sendCardTransfer/sendPhoneTransfer`
> через `@Before` — это дублирует вызов из `TransferServiceImpl`. Аспект нужно
> либо удалить, либо переориентировать на метрики/логирование без Kafka-вызовов.

---

#### 3.3 `report-service` не получает статусы платежей
**Проблема:** Если платёж создан, а потом заблокирован антифродом — в отчёте
навсегда останется статус `CREATED`.

**Исправление в `report-service`:**

`application.properties` — добавить:
```properties
mp.messaging.incoming.payment-status-changed.connector=smallrye-kafka
mp.messaging.incoming.payment-status-changed.topic=payment.status.changed
mp.messaging.incoming.payment-status-changed.group.id=report-service-status-group
mp.messaging.incoming.payment-status-changed.auto.offset.reset=earliest
```

`ReportService.java` — добавить обработчик:
```java
@Incoming("payment-status-changed")
@Blocking
@Transactional
public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
    log.infof("Report: payment status changed id=%d → %s",
              event.getPaymentId(), event.getNewStatus());
    // обновить статус в таблице payment_reports
    paymentReportRepository.updateStatus(event.getPaymentId(), event.getNewStatus());
}
```

---

#### 3.4 `card.created/blocked/unblocked/limit.changed` — никто не слушает
**Проблема:** `CardEventProducer` в account-service продюсирует 4 топика о картах,
но ни notification-service, ни history-service на них не подписаны.
Клиент не получает уведомления о блокировке карты.

**Исправление — добавить в notification-service:**

`application.properties`:
```properties
mp.messaging.incoming.card-events.connector=smallrye-kafka
mp.messaging.incoming.card-events.topics=card.created,card.blocked,card.unblocked,card.limit.changed
mp.messaging.incoming.card-events.group.id=notification-card-group
mp.messaging.incoming.card-events.auto.offset.reset=earliest
```

Новый класс `CardNotificationConsumer.java` в notification-service:
```java
@ApplicationScoped
public class CardNotificationConsumer {

    @Inject PushNotificationService pushService;

    @Incoming("card-events")
    @Blocking
    public void handleCardEvent(CardEvent event) {
        switch (event.getEventType()) {
            case "BLOCKED"       -> pushService.sendCardBlockedPush(event.getClientId(), event.getCardId());
            case "UNBLOCKED"     -> pushService.sendCardUnblockedPush(event.getClientId(), event.getCardId());
            case "CREATED"       -> pushService.sendCardCreatedPush(event.getClientId(), event.getCardId());
            case "LIMIT_CHANGED" -> pushService.sendCardLimitChangedPush(event.getClientId(), event.getCardId());
        }
    }
}
```

**Также** добавить в history-service подписку на `account.events` уже есть,
но card-события туда не попадают — нужно расширить `account.events` продюсером
из account-service через outbox или слушать `card.*` напрямую.

---

### 🟡 P1 — Архитектурный балласт и дублирование

#### 3.5 `suspicious-transfers.*` — 5 мёртвых топиков в antifraud
**Проблема:** antifraud объявляет и продюсирует в `suspicious-transfers.create/update/delete/get/Response`,
но никто на них не подписан через `@KafkaListener`. Это остаток старого CRUD-паттерна.
Актуальный поток идёт через `payment/transfer.antifraud.check/response`.

**Исправление:**
1. Удалить `@Bean` из `KafkaTopic.java` для `suspicious-transfers.update/delete/get/Response`
2. Удалить методы `SuspiciousTransferProducer`, которые шлют в эти топики
3. Оставить `suspicious-transfers.create` как входящий топик для transfer-service
   (transfer в outbox кладёт туда данные, а antifraud их мог бы слушать — но не слушает)
4. Либо удалить `suspicious-transfers.create` из outbox transfer-service,
   раз antifraud уже использует `transfer.antifraud.check`

#### 3.6 `transfer.account/card/phone` + `AuditAspect` — двойной путь
**Проблема:** `AuditAspect` перехватывает `save*Transfer()` и через `TransferProducer`
шлёт DTO в `transfer.account/card/phone`. Затем `TransferConsumer` принимает их
и снова вызывает `transferService.save*Transfer()` — **рекурсивный цикл**,
прерываемый только тем, что `ConcurrentHashMap pendingTransfers` ждёт `topicAccountDetailsGet`.

Это архитектурный анти-паттерн: один HTTP-запрос порождает Kafka-round-trip
внутри того же сервиса. При этом `topicAccountDetailsGet` никто не продюсирует.

**Исправление:**
- Удалить `AuditAspect` из transfer-service (он вызывает дублирующий Kafka-цикл)
- Удалить `TransferConsumer` (он ждёт ответа, который никогда не придёт)
- Оставить только прямой путь: `TransferServiceImpl` → outbox → antifraud/history/notification

#### 3.7 `external.audit.logs` — account шлёт, никто не читает
**Проблема:** `AuditProducer` в account-service шлёт аудит-ответы в `external.audit.logs`,
но ни один сервис на них не подписан.

**Исправление:** Либо удалить `external.audit.logs` и шлать в общий `audit.logs`,
либо подписать history-service через дополнительный канал.

---

### ℹ️ P2 — Внешние зависимости (не требуют изменений в монорепо)

#### 3.8 profile-service — нет продюсеров
`profile.create/update/delete/get` ждут команд от внешнего API-gateway или
admin-инструмента. В рамках монорепо продюсеров нет — это **ожидаемо**,
если profile управляется через отдельный фронтенд.
**Действие:** Задокументировать ожидаемых внешних продюсеров.

#### 3.9 public-info — нет продюсеров
Аналогично: `public-info.*` ждут событий от внешней admin-системы.
**Действие:** Задокументировать.

#### 3.10 authorization — `auth.login`, `user.*`
Ждут внешнего клиента (мобильное приложение / фронтенд).
**Действие:** Задокументировать, убедиться что фронтенд использует правильные имена.

---

## 4. Итоговый приоритетный список правок

| Приоритет | Файл | Изменение |
|---|---|---|
| 🔴 P0 | `transfer/KafkaErrorPublisher.java` | `"error.logging"` → `"error.logs"` |
| 🔴 P0 | `authorization/application-local.yaml` | `error-logging: error.logging` → `error.logs` |
| 🔴 P0 | `report-service/application.properties` | Добавить `payment.status.changed` |
| 🔴 P0 | `report-service/ReportService.java` | Добавить `@Incoming("payment-status-changed")` |
| 🔴 P0 | notification-service | Добавить `CardNotificationConsumer` для `card.*` |
| 🟡 P1 | `transfer/TransferProducer.java` | Удалить `sendAuditHistory()` |
| 🟡 P1 | `transfer/AuditAspect.java` | Убрать Kafka-вызовы, оставить только логирование |
| 🟡 P1 | `transfer/TransferConsumer.java` | Удалить (заменён outbox-паттерном) |
| 🟡 P1 | `antifraud/KafkaTopic.java` | Удалить `suspicious-transfers.update/delete/get/Response` |
| 🟡 P1 | `antifraud/SuspiciousTransferProducer.java` | Оставить только актуальные методы |
| 🟡 P1 | `account/AuditProducer.java` | `external.audit.logs` → `audit.logs` |
| ℹ️ P2 | `suspicious-transfers.Response` | Исправить опечатку → `suspicious-transfers.response` |

---

## 5. Целевая топик-карта (после рефакторинга)

```
ИТОГОВЫЕ АКТИВНЫЕ ТОПИКИ (после удаления мёртвых):

Платёжный домен (payment-api):
  payment.created              producer: payment-api     consumers: notification, report
  payment.status.changed       producer: payment-api     consumers: notification, report  ← добавить report
  payment.antifraud.check      producer: payment-api     consumers: antifraud
  payment.antifraud.response   producer: antifraud       consumers: payment-api
  account.events               producer: payment-api     consumers: history

Переводы (transfer):
  transfer.antifraud.check     producer: transfer        consumers: antifraud
  transfer.antifraud.response  producer: antifraud       consumers: transfer
  transfer.events              producer: transfer        consumers: history
  transfer.notification        producer: transfer        consumers: notification

Карты (account → notification):
  card.created                 producer: account         consumers: notification  ← добавить
  card.blocked                 producer: account         consumers: notification  ← добавить
  card.unblocked               producer: account         consumers: notification  ← добавить
  card.limit.changed           producer: account         consumers: notification  ← добавить

CRUD аккаунтов (account ↔ authorization):
  account.create/update/delete/get/getById         consumers: account
  external.account.create/update/delete/get/getById consumers: authorization
  auth.validate                producer: account         consumers: authorization
  auth.validate.response       producer: authorization   consumers: account

Аудит и ошибки:
  audit.logs     producers: antifraud, account, profile   consumers: history
  error.logs     producers: transfer, authorization, profile, public-info  consumers: history
  transfer.events → history
  account.events  → history

Справочные данные (внешние admin-системы → public-info):
  public-info.bank/branch/atm/certificate/license.create/update/delete

Профиль (внешний клиент → profile):
  profile.create/update/delete/get
  account.details.create/update/delete/get

Авторизация (внешний клиент → authorization):
  auth.login / auth.login.response
  user.create/update/delete/get + .response

DLQ (автоматические, обрабатываются history-service):
  audit-logs-dlq
  error-logs-dlq
  payment.created.dlq
  transfer.notification.dlq

УДАЛЯЮТСЯ (мёртвые топики):
  ❌ audit.history              (заменён transfer.events через outbox)
  ❌ error.logging              (опечатка, исправляется на error.logs)
  ❌ suspicious-transfers.update/delete/get/Response
  ❌ external.audit.logs        (дублирует audit.logs)
  ❌ topicAccountDetailsGet     (никто не продюсирует)
```

---

*Обновлён: автоматический анализ кода v5. Следующее обновление — после рефакторинга P0.*
