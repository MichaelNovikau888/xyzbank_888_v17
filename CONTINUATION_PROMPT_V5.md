# CONTINUATION_PROMPT_V5 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история)

### 1. history-service (Quarkus) — исключения и валидация
- `ConstraintViolationExceptionMapper`, `GenericExceptionMapper`, `IllegalArgumentExceptionMapper`, `NotFoundExceptionMapper` — добавлен `@ApplicationScoped`
- `HistoryResource` — добавлен `@ValidateOnExecution(type = ExecutableType.ALL)`
- `HistoryServiceImpl` — методы `getAuditHistoryByServiceName/EventType/TransferId` бросают `EntityNotFoundException` (→ 404) когда записей нет
- `pom.xml` history-service — Lombok 1.18.36, `lombok-mapstruct-binding:0.2.0`, правильный порядок annotationProcessorPaths

### 2. HistoryKafkaListener — ПОЛНАЯ ЗАМЕНА
- `ThreadLocal<MessageDigest>` — переиспользование SHA-256 digest на поток
- `HexFormat.of().formatHex()` вместо `StringBuilder + String.format`
- Idempotency через **ConstraintViolationException** вместо `existsByContentHash()` — убирает race condition
- Добавлены DLQ handlers: `handleAuditLogDlq`, `handleErrorLogDlq`

### 3. payment-api → antifraud интеграция (Spring Boot)
- `enums/FraudDecision.java` — ALLOW / REVIEW / BLOCK
- `dto/AntifraudRequestEvent.java`, `dto/AntifraudResponseEvent.java`
- `kafkaConsumer/SuspiciousTransferConsumer.java` — слушает `payment.antifraud.check`, отвечает в `payment.antifraud.response`
- `kafkaProducer/SuspiciousTransferProducer.java` — `sendAntifraudResponse()`
- `service/SuspiciousTransferServiceImpl.java` — ≤10k=ALLOW, 10k-50k=REVIEW, >50k=BLOCK

### 4. transfer → history интеграция
- `transfer/outbox/HistoryOutboxHelper.java` — шлёт в `transfer.events`
- `transfer/service/AuditServiceImpl.java` — заменён мёртвый топик `audit.history` на `historyOutboxHelper.enqueueTransferEvent()`

### 5. transfer → antifraud + notification интеграция
**В transfer:**
- `AntifraudRequestEvent.java`, `AntifraudResponseEvent.java`, `AntifraudOutboxHelper.java`
- `AntifraudResponseConsumer.java` — слушает `transfer.antifraud.response`, обновляет status, шлёт в `transfer.events` + `transfer.notification`
- `TransferNotificationOutboxHelper.java` — `enqueueCreated()` и `enqueueFinal()` → `transfer.notification`
- `TransferServiceImpl.java` — после save: 3 outbox-события в одной транзакции

**В antifraud:**
- `dto/TransferAntifraudRequestEvent.java`, `dto/TransferAntifraudResponseEvent.java`
- Второй `@KafkaListener` на `transfer.antifraud.check` в `SuspiciousTransferConsumer`
- `SuspiciousTransferProducer.sendTransferAntifraudResponse()` → `transfer.antifraud.response`

### 6. notification-service — полная реализация (Quarkus)
- `PushNotificationService.java` — FCM через `FcmClient`
- `EmailService.java` — шаблоны в snake_case
- `NotificationRecord.java` + `NotificationRecordService.java` — БД для финальных статусов
- `NotificationConsumer.java` — push + email для payment событий
- `TransferNotificationConsumer.java` — CREATED/COMPLETED/BLOCKED/CANCELLED логика
- `EmailService.sendTransferFinalNotification()` — шаблоны для переводов
- `PushNotificationService` — методы `sendTransferCreatedPush()`, `sendTransferFinalPush()`

### 7. Long → String рефакторинг (номера счётов/карт/телефонов)
**Причина:** номер счёта = 20 цифр, Long.MAX_VALUE = 19 цифр.

| Сервис | Файл | Поле |
|---|---|---|
| account | `entity/Account.java` | `accountNumber: Long → String` |
| account | `dto/AccountDto.java` | `accountNumber: Long → String` |
| transfer | `entity/CardTransfer.java` | `cardNumber: Long → String` |
| transfer | `entity/PhoneTransfer.java` | `phoneNumber: Long → String` |
| transfer | `dto/CardTransferDto.java`, `PhoneTransferDto.java` | аналогично |
| profile | `entity/Profile.java` | `phoneNumber: Long → String` |
| public-info | `entity/Branch.java` | `phoneNumber: Long → String` |

**Liquibase миграции:** account (`account-number-to-varchar.xml`), transfer (`phone-card-number-to-varchar.xml` + `account-number-to-varchar.xml`), profile, public-info.

### 8. Тесты — обновление и написание
- `TestConstants.java` — `PHONE_NUMBER/CARD_NUMBER: Long → String`, добавлены `CLIENT_ID`, `PURPOSE`
- `TransferServiceImplTest.java` — переписан: моки `AntifraudOutboxHelper`, `TransferNotificationOutboxHelper`, `OutboxRepository`, `ObjectMapper`, `TransferMetrics`; каждый `save*` проверяет 3 outbox-вызова + idempotency + ошибку репозитория
- `AntifraudResponseConsumerTest.java` — новый: ACCOUNT/CARD/PHONE × ALLOW/BLOCK, idempotency, not found
- `TransferNotificationConsumerTest.java` — новый QuarkusTest: CREATED/COMPLETED/BLOCKED/CANCELLED, Redis dedup, ошибки

### 9. Исправление багов (red flags в коде)
- `AntifraudResponseConsumer.java` — дублирующий тернарный оператор в маскировке карты
- `TransferServiceIntegrationTest.java` — `setCardNumber(Long)` → `setCardNumber(String)`, аналогично phoneNumber
- `AuditAspect.java` — `processTransfer(Long number)` → `processTransfer(String number)`, убран `processTransferAccount`, один унифицированный метод

### 10. Kafka-топик аудит и рефакторинг (последний чат)
**Обнаружены и задокументированы 12 проблем с топиками. Создан `KAFKA_TOPOLOGY.md` в корне проекта.**

**Применены исправления:**

| # | Проблема | Что сделано | Файлы |
|---|---|---|---|
| P0 | `error.logging` ≠ `error.logs` | Исправлено в 4 файлах | `KafkaErrorPublisher.java`, `authorization/application-local.yaml`, `authorization/application-prod.yaml`, `authorization/KafkaTopicConfig.java` |
| P0 | report-service не знал о смене статусов | Добавлен consumer `payment.status.changed` | `ReportService.java`, `report/application.properties` |
| P0 | `card.*` события терялись | Новый `CardNotificationConsumer` + методы push | `CardNotificationConsumer.java`, `CardNotificationEvent.java`, `PushNotificationService.java` (+4 метода), `CardEvent.java` (+clientId), `CardEventProducer.java`, `notification/application.properties` |
| P1 | `external.audit.logs` — мёртвый топик | Перенаправлен в `audit.logs` | `account/AuditProducer.java` |
| P1 | `suspicious-transfers.Response` — опечатка | Исправлена | `antifraud/KafkaTopic.java` |
| P1 | `AuditAspect` — дублирующий Kafka round-trip | Убраны все Kafka-вызовы, оставлено только логирование `@AfterReturning` | `transfer/AuditAspect.java` |

---

## Kafka-топик карта (актуальная)

```
payment-api → payment.created         → notification (push+email), report (CSV)
payment-api → payment.status.changed  → notification, report ✅ (исправлено)
payment-api → payment.antifraud.check → antifraud
antifraud   → payment.antifraud.response → payment-api
payment-api → account.events          → history-service

transfer → transfer.antifraud.check   → antifraud
antifraud → transfer.antifraud.response → transfer
transfer → transfer.events            → history-service
transfer → transfer.notification      → notification

account → card.created/blocked/unblocked/limit.changed → notification ✅ (исправлено)

account ↔ authorization: account.create/update/delete/get/getById + external.*
account ↔ authorization: auth.validate / auth.validate.response

audit.logs  → history-service   (producers: antifraud, account, profile)
error.logs  → history-service   (producers: transfer, authorization, profile)
```

---

## Что нужно сделать в следующем чате

### 1. 🔴 P1 — Удалить мёртвые топики из antifraud (балласт)

**`spring-boot-services/antifraud/src/main/java/com/bank/antifraud/config/KafkaTopic.java`**

Удалить `@Bean` методы для мёртвых топиков — они декларируются, но никто на них не подписан:
```java
// УДАЛИТЬ эти @Bean:
public NewTopic updateTopic()   { "suspicious-transfers.update" }
public NewTopic deleteTopic()   { "suspicious-transfers.delete" }
public NewTopic getTopic()      { "suspicious-transfers.get" }
public NewTopic responseTopic() { "suspicious-transfers.response" }
// ОСТАВИТЬ только:
public NewTopic createTopic()   { "suspicious-transfers.create" }  // transfer кладёт (legacy)
public NewTopic logsTopic()     { "audit.logs" }
public NewTopic paymentAntifraudCheckTopic()
public NewTopic paymentAntifraudResponseTopic()
public NewTopic transferAntifraudCheckTopic()
public NewTopic transferAntifraudResponseTopic()
```

**`spring-boot-services/antifraud/src/main/java/com/bank/antifraud/kafkaProducer/SuspiciousTransferProducer.java`**

Убрать методы, которые шлют в мёртвые топики:
```java
// УДАЛИТЬ методы, использующие:
kafkaTemplate.send("suspicious-transfers.update", ...)
kafkaTemplate.send("suspicious-transfers.delete", ...)
kafkaTemplate.send("suspicious-transfers.get", ...)
// ОСТАВИТЬ только sendAntifraudResponse() и sendTransferAntifraudResponse()
```

---

### 2. 🔴 P1 — Удалить мёртвый TransferConsumer

**`spring-boot-services/transfer/src/main/java/com/bank/transfer/kafka/TransferConsumer.java`**

Этот класс создавал антипаттерн: `AuditAspect` (через `@Before`) слал DTO в `transfer.account/card/phone`, а `TransferConsumer` принимал их обратно в том же сервисе и хранил в `ConcurrentHashMap pendingTransfers`, ожидая ответа на `topicAccountDetailsGet` — который **никто никогда не продюсирует**.

Сейчас `AuditAspect` уже исправлен (Kafka-вызовы убраны), поэтому `TransferConsumer` не получает входящих сообщений. Его можно безопасно удалить.

**Также удалить из `TransferProducer.java`:**
- Метод `sendAuditHistory()` — шлёт в `audit.history`, который никто не слушает (в javadoc уже помечен как deprecated)
- Методы `sendAccountTransferToSuspicious()`, `sendCardTransferToSuspicious()`, `sendPhoneTransferToSuspicious()` — шлют в `suspicious-transfers.create`, но antifraud уже использует `transfer.antifraud.check` через outbox

**Что оставить в `TransferProducer.java`:**
- `sendAccountTransfer()`, `sendCardTransfer()`, `sendPhoneTransfer()` — шлют в `transfer.account/card/phone`

> ⚠️ Проверить: после удаления `TransferConsumer` убедиться, что `transfer.account/card/phone` топики тоже можно удалить. Если никто другой на них не подписан — они тоже становятся мёртвыми. `AuditConsumer` в transfer слушает их для аудита — уточнить нужно ли это, так как `AuditServiceImpl` уже пишет историю через `HistoryOutboxHelper` → `transfer.events`.

---

### 3. 🟡 P1 — Валидация String-номеров через @Pattern

Добавить `@Pattern` аннотации в DTO-классы transfer и account:

**`transfer/src/main/java/com/bank/transfer/dto/AccountTransferDto.java`**
```java
@Pattern(regexp = "\\d{20}", message = "Account number must be exactly 20 digits")
private String accountNumber;
```

**`transfer/src/main/java/com/bank/transfer/dto/CardTransferDto.java`**
```java
@Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits")
private String cardNumber;
```

**`transfer/src/main/java/com/bank/transfer/dto/PhoneTransferDto.java`**
```java
@Pattern(regexp = "\\+?[0-9]{10,15}", message = "Invalid phone number format")
private String phoneNumber;
```

**`account/src/main/java/com/bank/account/dto/AccountDto.java`**
```java
@Pattern(regexp = "\\d{20}", message = "Account number must be exactly 20 digits")
private String accountNumber;
```

---

### 4. 🟡 P1 — docker-compose: добавить новые Kafka-топики

В `docker-compose.yml` нет создания новых топиков. Добавить в секцию kafka или в `kafka-setup` сервис:

```yaml
# Топики которые нужно добавить (сейчас отсутствуют в docker-compose):
transfer.antifraud.check
transfer.antifraud.response
transfer.notification
transfer.notification.dlq
transfer.events
account.events
card.created
card.blocked
card.unblocked
card.limit.changed
card.events.dlq
payment.created.dlq
```

---

### 5. 🟡 P1 — Технические долги notification-service

**`ClientService.java`** — email хардкодом `novikovmm1981@gmail.com`:
```java
// Текущее состояние — заглушка:
public String getClientEmail(String clientId) {
    return "novikovmm1981@gmail.com"; // TODO: REST-клиент к profile-service
}
```
Нужен REST-клиент (MicroProfile RestClient) к profile-service для получения реального email по `clientId`.

**`PushNotificationService.fetchPushTokenFromProfile()`** — возвращает null:
```java
private String fetchPushTokenFromProfile(String clientId) {
    return null; // TODO: REST-клиент к profile-service
}
```
Пуш-уведомления сейчас не работают — нет токена. Нужен вызов к profile-service.

---

### 6. ℹ️ P2 — Проверить маппинг в AuditConsumer (transfer)

**`transfer/src/main/java/com/bank/transfer/kafka/AuditConsumer.java`**

Этот класс слушает `transfer.account/card/phone` и вызывает `AuditServiceImpl`. Но `AuditServiceImpl` уже пишет в `transfer.events` через `HistoryOutboxHelper` при сохранении перевода в `TransferServiceImpl`. Возможно двойное аудит-событие:

1. `TransferServiceImpl.save*()` → `AuditServiceImpl.audit*()` → `HistoryOutboxHelper` → `transfer.events`
2. `AuditConsumer` (слушает `transfer.account`) → тоже вызывает `AuditServiceImpl.audit*()` → снова `transfer.events`

Нужно выяснить и устранить дублирование.

---

### 7. ℹ️ P2 — Тест для CardNotificationConsumer (новый класс)

Написать `CardNotificationConsumerTest` в notification-service по аналогии с `TransferNotificationConsumerTest`:

```
Сценарии:
  eventType=CREATED      → sendCardCreatedPush() вызван
  eventType=BLOCKED      → sendCardBlockedPush() вызван
  eventType=UNBLOCKED    → sendCardUnblockedPush() вызван
  eventType=LIMIT_CHANGED → sendCardLimitChangedPush() вызван
  дубль Redis            → ничего не вызывается
  невалидный JSON        → EmailsFailed.increment(), нет исключения наружу
  clientId=null          → fallback на accountId.toString()
```

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн (OutboxRelayScheduler) во всех Spring Boot сервисах
- **PostgreSQL**: каждый сервис своя схема; Liquibase для Spring Boot, Hibernate ddl-auto для Quarkus
- **Redis**: notification-service (dedup + template cache + push token cache)
- **FCM**: Firebase Cloud Messaging для push-уведомлений

---

## Как начать следующий чат

Приложи архив `xyzbank_v5.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V5.md внутри архива и продолжай работу.
Следующая задача: удалить мёртвые топики из antifraud (KafkaTopic.java +
SuspiciousTransferProducer.java), удалить TransferConsumer.java и лишние методы
из TransferProducer.java, добавить @Pattern валидацию в DTO, добавить топики
в docker-compose.yml.
```
