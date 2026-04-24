# CONTINUATION_PROMPT_V3 — XYZBank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что уже сделано в предыдущих чатах

### 1. Исключения history-service (Quarkus)
- `ConstraintViolationExceptionMapper`, `GenericExceptionMapper`, `IllegalArgumentExceptionMapper`, `NotFoundExceptionMapper` — добавлен `@ApplicationScoped` чтобы IDEA не жаловалась "never used"
- `HistoryResource` — добавлен `@ValidateOnExecution(type = ExecutableType.ALL)` для активации Bean Validation на `@QueryParam`
- `HistoryServiceImpl` — методы `getAuditHistoryByServiceName`, `getAuditHistoryByEventType`, `getAuditHistoryByTransferId` теперь бросают `EntityNotFoundException` (→ 404) когда записей нет; добавлены проверки на blank; исправлен порядок count→find
- `pom.xml` history-service — Lombok обновлён до 1.18.36 (совместимость с JDK 21), добавлен `lombok-mapstruct-binding:0.2.0`, исправлен порядок annotationProcessorPaths: lombok → binding → mapstruct-processor

### 2. Kafka топик-карта (выяснено)
- `payment-api` → `payment.created`, `payment.status.changed`
- `transfer` → `transfer.account`, `transfer.card`, `transfer.phone`, `suspicious-transfers.create`, `transfer.events` (через outbox)
- `antifraud` слушает `suspicious-transfers.create` + теперь `payment.antifraud.check`; отвечает в `payment.antifraud.response`
- `history-service` слушает: `audit.logs`, `transfer.events`, `account.events`, `error.logs`
- `notification-service` слушает: `payment.created`, `payment.status.changed`
- `report-service` слушает: `payment.created`

### 3. payment-api → antifraud интеграция (СДЕЛАНО)
Файлы в архиве в соответствующих пакетах:

**antifraud:**
- `enums/FraudDecision.java` — новый enum: ALLOW / REVIEW / BLOCK
- `dto/AntifraudRequestEvent.java` — что payment-api шлёт в antifraud
- `dto/AntifraudResponseEvent.java` — что antifraud отвечает
- `kafkaConsumer/SuspiciousTransferConsumer.java` — переписан: слушает `payment.antifraud.check`, анализирует, отвечает AntifraudResponseEvent
- `kafkaProducer/SuspiciousTransferProducer.java` — добавлен `sendAntifraudResponse()`
- `service/SuspiciousTransferServiceImpl.java` — трёхуровневая логика: ≤10k=ALLOW, 10k-50k=REVIEW, >50k=BLOCK
- `config/KafkaConfigConsumer.java` — добавлен `antifraudRequestListenerContainerFactory`

**payment-api:**
- `antifraud/AntifraudRequestEvent.java` — DTO запроса
- `antifraud/AntifraudResponseEvent.java` — DTO ответа
- `antifraud/AntifraudKafkaProducer.java` — через outbox шлёт в `payment.antifraud.check`
- `antifraud/AntifraudResponseConsumer.java` — слушает `payment.antifraud.response`, обновляет статус платежа + шлёт в history
- `config/KafkaConsumerConfig.java` — `antifraudResponseListenerContainerFactory`
- `service/PaymentService.java` — добавлен `enqueueAntifraudCheck()` после сохранения платежа
- `history/HistoryOutboxHelper.java` — шлёт в `account.events` через outbox

**Поток платежа:**
```
POST /api/v1/payments
→ Payment(CREATED) сохранён в БД
→ outbox: payment.created + payment.antifraud.check
→ antifraud анализирует → AntifraudResponseEvent(ALLOW/REVIEW/BLOCK)
→ payment-api обновляет статус:
    ALLOW  → PROCESSING
    REVIEW → PROCESSING + reason
    BLOCK  → FAILED
→ outbox: account.events → history-service
```

### 4. transfer → history интеграция (СДЕЛАНО)
- `transfer/outbox/HistoryOutboxHelper.java` — шлёт в `transfer.events` (топик который history реально слушает)
- `transfer/service/AuditServiceImpl.java` — заменён `transferProducer.sendAuditHistory()` (слал в мёртвый топик `audit.history`) на `historyOutboxHelper.enqueueTransferEvent()`

### 5. Исправления тестов
- `ReportServiceTest.java` — исправлен doAnswer: `recordCallable` → `record(Supplier)` (такого метода в Timer не было)
- `PaymentIntegrationTest.java` — исправлена переменная `key` (не была объявлена): теперь берётся из `paymentRepository.findById(paymentId).getIdempotencyKey()`

---

## Что нужно сделать в следующем чате

### ГЛАВНАЯ ЗАДАЧА: notification-service (Quarkus)

**Текущее состояние:**
- `serde/PaymentCreatedEventDeserializer.java` — полностью закомментирован, не нужен (Quarkus Kafka десериализует через Jackson автоматически). **Удалить папку serde целиком.**
- `PushNotificationService.java` — полностью закомментирован, нужно раскомментировать и доделать
- `PushTokenResource.java` — полностью закомментирован, нужно раскомментировать
- `PaymentStatusChangedEvent.java` — не используется нигде, нужно подключить
- `EmailService.java` — метод `sendPaymentNotification` закомментировал `mailer.send()` — нужно раскомментировать
- `ClientService.java` — заглушка, email хардкодом `novikovmm1981@gmail.com`

**Что нужно реализовать:**

#### A. Push-уведомления по статусам
```
CREATED    → push "Платёж создан на сумму X RUB"
PROCESSING → push "Платёж в обработке, займёт некоторое время"
COMPLETED  → push "Платёж успешно выполнен ✅"
FAILED     → push "Платёж заблокирован ❌"
CANCELLED  → push "Платёж отменён"
```

#### B. Email только для конечных статусов
```
COMPLETED → подробный email: сумма, получатель, дата, время
FAILED    → email с причиной блокировки/ошибки
CANCELLED → email с причиной отмены
```

#### C. Запись в БД краткой записи о конечном результате
Нужна новая entity `NotificationRecord` (таблица `notification.notification_records`):
```java
- paymentId
- clientId
- finalStatus (COMPLETED/FAILED/CANCELLED)
- amount
- currency
- recipientAccount
- reason
- notifiedAt
```
Чтобы клиент мог через REST посмотреть историю своих платёжных уведомлений.

#### D. Redis использование
- Кэш push-токенов: `push:token:{clientId}` TTL=30 дней
- Кэш email-шаблонов: уже есть в EmailService (`email:template:{name}`)
- Dedup ключи: уже есть в NotificationConsumer (`notif:processed:{paymentId}:{eventType}`)

#### E. FcmClient — реальная интеграция
Сейчас `FcmClient` — интерфейс без реализации. Нужно:
1. Раскомментировать `PushNotificationService`
2. Исправить двойной `@PostConstruct` (баг в закомментированном коде)
3. Настроить `application.properties`: `quarkus.rest-client.fcm-api.url=https://fcm.googleapis.com`
4. Добавить FCM server key в properties: `fcm.server-key=${FCM_SERVER_KEY:test-key}`

#### F. NotificationConsumer — добавить push в handlePaymentCreated и handlePaymentStatusChanged
Сейчас консьюмер шлёт только email. Нужно добавить вызов `PushNotificationService` для каждого статуса.

#### G. REST endpoint для просмотра истории уведомлений клиента
```
GET /api/v1/notifications/{clientId}
```

#### H. Тест с реальной отправкой
Для теста push на реальный телефон:
1. Зарегистрировать FCM токен своего телефона через `POST /api/v1/push/register`
2. Написать `@QuarkusIntegrationTest` который создаёт платёж (через Kafka in-memory) и проверяет что push ушёл

Для теста email на реальный ящик:
- `application.properties` уже настроен на MailHog (localhost:1025)
- Для реального Gmail нужно: `quarkus.mailer.host=smtp.gmail.com`, `port=587`, `start-tls=REQUIRED`, `username/password`
- В тесте: убрать `%test.quarkus.mailer.mock=true` и использовать реальный SMTP

### 2. transfer → antifraud интеграция (аналог payment-api)
По той же схеме что payment-api:
- Transfer перед исполнением шлёт в `transfer.antifraud.check`
- Antifraud проверяет и отвечает в `transfer.antifraud.response`
- Transfer обновляет статус перевода и шлёт в `transfer.events` → history

Нужно создать:
- `transfer/antifraud/AntifraudRequestEvent.java`
- `transfer/antifraud/AntifraudResponseConsumer.java`
- Добавить `antifraudCheckListenerContainerFactory` в antifraud KafkaConfigConsumer
- Добавить второй `@KafkaListener` в `SuspiciousTransferConsumer` на топик `transfer.antifraud.check`

### 3. Мелкие технические долги
- `ExampleResource.java` и `MyMessagingApplication.java` в notification-service — удалить (заглушки от Quarkus scaffold)
- `ExampleResourceIT.java`, `ExampleResourceTest.java`, `MyMessagingApplicationTest.java` — удалить
- В `EmailService.getTemplateNameForStatus()` имена шаблонов на кириллице (`"платёж_создан"`) — переименовать в snake_case латиницей, согласовать с `import.sql` (`payment_created`, `payment_processing`, etc.)
- `PaymentStatusChangedEvent` в notification-service — не хватает поля `reason` для передачи причины блокировки в email

---

## Технологический стек
- **Spring Boot сервисы**: account, antifraud, authorization, transfer, payment-api
- **Quarkus сервисы**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн везде (OutboxRelayScheduler)
- **PostgreSQL**: каждый сервис своя схема
- **Redis**: notification-service (dedup + template cache + push token cache)
- **FCM**: Firebase Cloud Messaging для push-уведомлений
- **Quarkus Mailer**: email через SMTP/MailHog

## Как начать следующий чат
Приложи архив `xyzbank_continuation.zip` и напиши:
```
Прочитай CONTINUATION_PROMPT_V3.md внутри архива и продолжай работу.
Следующая задача: notification-service — раскомментировать PushNotificationService,
подключить к NotificationConsumer, реализовать запись в БД, тест с реальной отправкой.
```
