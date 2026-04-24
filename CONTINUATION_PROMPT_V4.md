# CONTINUATION_PROMPT_V4 — XYZBank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история)

### 1. history-service (Quarkus) — исключения и валидация
- `ConstraintViolationExceptionMapper`, `GenericExceptionMapper`, `IllegalArgumentExceptionMapper`, `NotFoundExceptionMapper` — добавлен `@ApplicationScoped`
- `HistoryResource` — добавлен `@ValidateOnExecution(type = ExecutableType.ALL)`
- `HistoryServiceImpl` — методы `getAuditHistoryByServiceName/EventType/TransferId` бросают `EntityNotFoundException` (→ 404) когда записей нет; проверки на blank; исправлен порядок count→find
- `pom.xml` history-service — Lombok 1.18.36, `lombok-mapstruct-binding:0.2.0`, правильный порядок annotationProcessorPaths

### 2. HistoryKafkaListener — ПОЛНАЯ ЗАМЕНА (сделано в последнем чате)
Файл: `history-service-quarkus/src/main/java/com/bank/history/kafka/HistoryKafkaListener.java`

**Что изменилось:**
- `ThreadLocal<MessageDigest>` — переиспользование SHA-256 digest на поток (экономия ~2 мкс на сообщение)
- `HexFormat.of().formatHex()` вместо `StringBuilder + String.format` (~300 нс вместо ~3 мкс)
- Idempotency через **ConstraintViolationException** вместо `existsByContentHash()` — убирает race condition: два потока могут одновременно пройти check, но только один INSERT пройдёт; второй получит исключение уникального индекса — это нормально, перехватываем и логируем как debug
- Добавлены DLQ handlers: `handleAuditLogDlq`, `handleErrorLogDlq`

### 3. payment-api → antifraud интеграция (Spring Boot)
**antifraud:**
- `enums/FraudDecision.java` — ALLOW / REVIEW / BLOCK
- `dto/AntifraudRequestEvent.java`, `dto/AntifraudResponseEvent.java`
- `kafkaConsumer/SuspiciousTransferConsumer.java` — слушает `payment.antifraud.check`, отвечает в `payment.antifraud.response`
- `kafkaProducer/SuspiciousTransferProducer.java` — `sendAntifraudResponse()`
- `service/SuspiciousTransferServiceImpl.java` — ≤10k=ALLOW, 10k-50k=REVIEW, >50k=BLOCK
- `config/KafkaConfigConsumer.java` — `antifraudRequestListenerContainerFactory`

**payment-api:**
- `antifraud/AntifraudRequestEvent.java`, `antifraud/AntifraudResponseEvent.java`
- `antifraud/AntifraudKafkaProducer.java` — через outbox → `payment.antifraud.check`
- `antifraud/AntifraudResponseConsumer.java` — слушает `payment.antifraud.response`
- `config/KafkaConsumerConfig.java` — `antifraudResponseListenerContainerFactory`
- `history/HistoryOutboxHelper.java` — шлёт в `account.events`

**Поток:**
```
POST /api/v1/payments → Payment(CREATED) → outbox: payment.created + payment.antifraud.check
→ antifraud → AntifraudResponseEvent(ALLOW/REVIEW/BLOCK)
→ payment-api: ALLOW→PROCESSING, REVIEW→PROCESSING, BLOCK→FAILED
→ outbox: account.events → history-service
```

### 4. transfer → history интеграция
- `transfer/outbox/HistoryOutboxHelper.java` — шлёт в `transfer.events`
- `transfer/service/AuditServiceImpl.java` — заменён мёртвый топик `audit.history` на `historyOutboxHelper.enqueueTransferEvent()`

### 5. transfer → antifraud интеграция (Задача 2)
**В transfer (новый пакет `com.bank.transfer.antifraud`):**
- `AntifraudRequestEvent.java` — DTO запроса: `transferId`, `transferType`, `amount`, `purpose`
- `AntifraudResponseEvent.java` — DTO ответа: `transferId`, `transferType`, `decision` (String), `reason`, `riskScore`
- `AntifraudOutboxHelper.java` — кладёт в outbox → `transfer.antifraud.check`
- `AntifraudResponseConsumer.java` — слушает `transfer.antifraud.response`, обновляет status в entity, шлёт в `transfer.events` + `transfer.notification`
- `config/KafkaConfigConsumer.java` — добавлена `transferAntifraudResponseListenerContainerFactory`

**В antifraud:**
- `dto/TransferAntifraudRequestEvent.java`, `dto/TransferAntifraudResponseEvent.java` — отдельные DTO для transfer
- `SuspiciousTransferConsumer.java` — второй `@KafkaListener` на `transfer.antifraud.check` (groupId=`anti_fraud-transfer-group`, factory=`antifraudTransferCheckListenerContainerFactory`)
- `SuspiciousTransferProducer.java` — `sendTransferAntifraudResponse()` → `transfer.antifraud.response`
- `KafkaConfigConsumer.java` — `antifraudTransferCheckListenerContainerFactory`
- `KafkaTopic.java` — добавлены топики `transfer.antifraud.check`, `transfer.antifraud.response`

**Топики:**
```
transfer.antifraud.check    — transfer → antifraud (запрос проверки)
transfer.antifraud.response — antifraud → transfer (ответ)
```

### 6. notification-service — полная реализация (Задача 1 + Задача 3)

**notification-service (Quarkus):**

**Задача 1 — push + email для платежей:**
- `PushNotificationService.java` — раскомментирован, исправлен двойной `@PostConstruct`, FCM через `FcmClient`
- `EmailService.java` — `mailer.send()` раскомментирован, шаблоны переименованы в snake_case латиницей
- `NotificationRecord.java` — entity `notification.notification_records` (финальные статусы)
- `NotificationRecordService.java` — `saveIfFinal()` для COMPLETED/FAILED/CANCELLED/BLOCKED
- `NotificationConsumer.java` — push + email + DB для payment событий
- `PushTokenResource.java` — `POST /api/v1/push/register`
- `NotificationResource.java` — `GET /api/v1/notifications/{clientId}?page=0&size=20`
- Удалены: `ExampleResource.java`, `MyMessagingApplication.java`, папка `serde/`, scaffold-тесты
- `pom.xml` — добавлен `quarkus-jdbc-h2` для тестов

**Задача 3 — push + email для переводов:**
- `TransferNotificationEvent.java` (event DTO) — `transferId`, `clientId`, `transferType`, `status`, `amount`, `currency`, `recipientDisplay`, `purpose`, `reason`, `occurredAt`
- `TransferNotificationConsumer.java` — слушает `transfer.notification`:
  - `CREATED` → только push «Перевод создан на сумму X → получатель»
  - `COMPLETED` → push + подробный email + запись в БД
  - `BLOCKED` → push + email с причиной + запись в БД
  - `CANCELLED` → push + email с причиной + запись в БД
- `EmailService.java` — добавлен `sendTransferFinalNotification()` с плейсхолдерами `{transfer_id}`, `{transfer_type}`, `{amount}`, `{currency}`, `{recipient_display}`, `{purpose}`, `{occurred_date}`, `{block_reason}`, `{cancel_reason}`
- `PushNotificationService.java` — добавлены `sendTransferCreatedPush()`, `sendTransferFinalPush()`
- `import.sql` — добавлены шаблоны: `transfer_completed`, `transfer_blocked`, `transfer_cancelled`
- `application.properties` — новый channel `transfer-notification` → топик `transfer.notification`

### 7. transfer-service — поля status + clientId + уведомления (Задача 3)
- `TransferStatus.java` (enum) — `CREATED`, `COMPLETED`, `BLOCKED`, `CANCELLED`
- Entity `AccountTransfer`, `CardTransfer`, `PhoneTransfer` — добавлены поля `clientId` (String) и `status` (TransferStatus)
- DTO `AccountTransferDto`, `CardTransferDto`, `PhoneTransferDto` — добавлен `clientId`
- `TransferNotificationOutboxHelper.java` — `enqueueCreated()` и `enqueueFinal()` → topik `transfer.notification`
- `TransferServiceImpl.java` — после save: outbox для `suspicious-transfers.create` + `transfer.antifraud.check` + `transfer.notification` (CREATED)
- `AntifraudResponseConsumer.java` — обновляет status в entity + `enqueueFinal()` при ALLOW→COMPLETED, BLOCK→BLOCKED

### 8. Long → String рефакторинг (сделано в последнем чате)
**Причина:** номер счёта = 20 цифр, Long.MAX_VALUE = 19 цифр — невозможно хранить.

**Затронутые файлы и поля:**

| Сервис | Файл | Поле |
|---|---|---|
| account | `entity/Account.java` | `accountNumber: Long → String` |
| account | `dto/AccountDto.java` | `accountNumber: Long → String` |
| account | `repository/AccountRepository.java` | параметры `findBy/existsBy` |
| account | `test/.../TestUtils.java` | сигнатуры `createAccount/createAccountDto` |
| account | `test/.../AccountCommandConsumerIntegrationTest.java` | `buildAccountDto` сигнатура + вызовы (реальные 20-значные номера) |
| transfer | `entity/CardTransfer.java` | `cardNumber: Long → String` |
| transfer | `entity/PhoneTransfer.java` | `phoneNumber: Long → String` |
| transfer | `dto/CardTransferDto.java` | `cardNumber: Long → String` |
| transfer | `dto/PhoneTransferDto.java` | `phoneNumber: Long → String` |
| transfer | `repository/CardTransferRepository.java` | параметр `existsByCardNumber...` |
| transfer | `repository/PhoneTransferRepository.java` | параметр `existsByPhoneNumber...` |
| profile | `entity/Profile.java` | `phoneNumber: Long → String` |
| profile | `dto/ProfileDto.java` | `phoneNumber: Long → String` |
| public-info | `entity/Branch.java` | `phoneNumber: Long → String` |
| public-info | `dto/BranchDto.java` | `phoneNumber: Long → String` |

**Liquibase миграции добавлены:**

| Сервис | Файл миграции | Изменение |
|---|---|---|
| transfer | `release-0.3.0.0/account-number-to-varchar.xml` | уже был, `account_number BIGINT→VARCHAR(20)` |
| transfer | `release-0.4.0.0/phone-card-number-to-varchar.xml` | `card_number BIGINT→VARCHAR(19)`, `phone_number BIGINT→VARCHAR(20)`, добавлены колонки `status VARCHAR(20)`, `client_id VARCHAR(100)` |
| account | `2024/04/account-number-to-varchar.xml` | `account_number BIGINT→VARCHAR(20)` |
| profile | `release-0.2.0.0/phone-number-to-varchar.xml` | `phone_number BIGINT→VARCHAR(20)` |
| public-info | `release-0.2.0.0/phone-number-to-varchar.xml` | `branch.phone_number BIGINT→VARCHAR(20)` (с пересозданием unique constraint) |

---

## Kafka топик-карта (актуальная)

```
payment-api       → payment.created, payment.status.changed, payment.antifraud.check
antifraud         → payment.antifraud.response, transfer.antifraud.response
transfer          → transfer.account, transfer.card, transfer.phone
                    suspicious-transfers.create, transfer.events (через outbox)
                    transfer.antifraud.check (через outbox)
                    transfer.notification (через outbox)
history-service   слушает: audit.logs, transfer.events, account.events, error.logs
notification-service слушает: payment.created, payment.status.changed, transfer.notification
report-service    слушает: payment.created
```

---

## Что нужно сделать в следующем чате

### 1. Тесты для новых компонентов
**transfer-service:**
- `TransferServiceImplTest` — обновить: методы `save*` теперь принимают String cardNumber/phoneNumber и вызывают 3 outbox-метода вместо 1; проверить что `antifraudOutboxHelper.enqueue*` и `notificationOutboxHelper.enqueueCreated` вызваны
- `AntifraudResponseConsumerTest` — новый тест: при `decision=BLOCK` → entity.status=BLOCKED + `notificationOutboxHelper.enqueueFinal` вызван; при `ALLOW` → COMPLETED

**notification-service:**
- `TransferNotificationConsumerTest` — тест: CREATED → только push (email не вызван); COMPLETED → push+email+DB; BLOCKED → push+email(с reason)+DB

### 2. Маппер transfer — String поля
- `AccountTransferMapper`, `CardTransferMapper`, `PhoneTransferMapper` — проверить/обновить маппинг `cardNumber`/`phoneNumber` (MapStruct может жаловаться на смену типа Long → String если поле маппируется с конвертацией)

### 3. account-service — проверить все места использования accountNumber
- `AccountCommandConsumer.java` — принимает AccountDto, проверить что нет явного каста к Long
- Все сервисы которые создают/читают Account — проверить что передают String, не Long

### 4. Валидация String номеров
Добавить `@Pattern` аннотации для проверки форматов:
```java
// accountNumber — 20 цифр
@Pattern(regexp = "\\d{20}", message = "Account number must be 20 digits")
private String accountNumber;

// cardNumber — 13-19 цифр
@Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits")
private String cardNumber;

// phoneNumber — международный формат
@Pattern(regexp = "\\+?[0-9]{10,15}", message = "Invalid phone number format")
private String phoneNumber;
```

### 5. Мелкие технические долги
- `ClientService.java` в notification-service — email хардкодом `novikovmm1981@gmail.com`; нужен REST-клиент к profile-service для получения реального email по clientId
- `fetchPushTokenFromProfile()` в `PushNotificationService` — возвращает null; нужен вызов к profile-service
- В `EmailService.sendPaymentNotification()` — `currency` хардкодом не передаётся (нет в `PaymentCreatedEvent`?)
- `report-service` — проверить что слушает `payment.created` корректно после изменений

### 6. Docker-compose обновление
Добавить в `docker-compose.yml` создание новых Kafka топиков:
```
transfer.antifraud.check
transfer.antifraud.response
transfer.notification
transfer.notification.dlq
```

---

## Технологический стек
- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн (OutboxRelayScheduler) во всех Spring Boot сервисах
- **PostgreSQL**: каждый сервис своя схема; Liquibase для Spring Boot, Hibernate ddl-auto для Quarkus
- **Redis**: notification-service (dedup + template cache + push token cache)
- **FCM**: Firebase Cloud Messaging для push-уведомлений

## Как начать следующий чат
Приложи архив `xyzbank_v4.zip` и напиши:
```
Прочитай CONTINUATION_PROMPT_V4.md внутри архива и продолжай работу.
Следующая задача: обновить тесты TransferServiceImplTest и написать
AntifraudResponseConsumerTest и TransferNotificationConsumerTest.
```
