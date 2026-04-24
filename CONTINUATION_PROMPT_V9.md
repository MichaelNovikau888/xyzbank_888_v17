# CONTINUATION_PROMPT_V9 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v9)

### Чаты 1–8 (см. CONTINUATION_PROMPT_V8.md)
<вся история — antifraud, transfer, notification, JaCoCo, REVIEW статус,
AntifraudResponseConsumer, Phase 1 Security Fix (JWT вместо Client-Id). Подробности в V8.md>

### Чат 9 — Phase 2: User Registration Flow (ВОПРОС 3, Task 2.1–2.4)

#### Задача 1 ✅: DTO (authorization-service)
- `dto/UserRegistrationRequest.java` — валидация: `@Email`, `@NotBlank`, `@Size(8-255)`,
  `@Pattern` (upper+lower+digit+special для пароля), phone `+?[0-9]{10,15}`
- `dto/UserRegistrationResponse.java` — `userId`, `email`, `requiresEmailVerification`, `message`

#### Задача 2 ✅: Event (authorization-service)
- `event/UserRegisteredEvent.java` — payload для топика `auth.user.registered`:
  `userId`, `email`, `fullName`, `phoneNumber`, `registeredAt`

#### Задача 3 ✅: UserRegistrationService (authorization-service)
- `service/UserRegistrationService.java`:
  1. `userRepository.existsByEmail()` → если true → `EntityExistsException`
  2. Создаёт `User(role=ROLE_USER, status=PENDING_VERIFICATION, profileId=0L)`
  3. В той же транзакции: `outboxHelper.enqueue("auth.user.registered", userId, "UserRegistered", event)`
- Вложенный enum `UserStatus`: `PENDING_VERIFICATION / ACTIVE / BLOCKED`

#### Задача 4 ✅: AuthController (authorization-service)
- `controller/AuthController.java`:
  - `POST /api/v1/auth/register` → 201 Created / 409 Conflict
  - Javadoc: основной login/validate flow по-прежнему через Kafka

#### Задача 5 ✅: User entity расширен
- `entity/User.java` — добавлены поля:
  `email VARCHAR(255) UNIQUE`, `fullName`, `phoneNumber VARCHAR(20)`, `status VARCHAR(30)`
- Все поля nullable (не ломают существующих profileId-based пользователей)

#### Задача 6 ✅: UserRepository расширен
- `repository/UserRepository.java` — добавлены:
  `existsByEmail(String email)`, `findByEmail(String email)`

#### Задача 7 ✅: Liquibase миграция
- `db/changelog/release-0.3.0.0/add-registration-fields.xml` — добавляет
  `email`, `full_name`, `phone_number`, `status` в таблицу `users`
- `db.changelog-master.yaml` — включает новую миграцию

#### Задача 8 ✅: SecurityConfig обновлён
- `/api/v1/auth/**` добавлен в `permitAll()` (без JWT)
- Комментарий вынесен перед блоком (не внутри varargs — синтаксическая корректность)

#### Задача 9 ✅: springdoc добавлен в authorization/pom.xml
- `springdoc-openapi-starter-webmvc-ui` (version управляется parent POM)

#### Задача 10 ✅: notification-service — Task 2.4
- `event/UserRegisteredEvent.java` — десериализация из Kafka (`@JsonIgnoreProperties`)
- `consumer/RegistrationNotificationConsumer.java`:
  - `@Incoming("auth-user-registered")`
  - Redis dedup: `notif:registration:{userId}` TTL=24ч, fail-open
  - `emailService.sendWelcomeEmail()` + `pushService.sendWelcomePush()`
  - Ошибки поглощаются (SmallRye → DLQ)
- `service/EmailService.java` — добавлен `sendWelcomeEmail()`:
  приветственное письмо на email клиента через Quarkus Mailer
- `service/PushNotificationService.java` — добавлен `sendWelcomePush()`:
  «🏦 Добро пожаловать в XYZ-Bank!»
- `metrics/NotificationMetrics.java` — добавлен counter `notificationSent`
- `application.properties` — добавлен канал `auth-user-registered`:
  topic=`auth.user.registered`, DLQ=`auth.user.registered.dlq`

#### Задача 11 ✅: Тесты
- `authorization/service/UserRegistrationServiceTest.java` — 5 тестов:
  1. Успешная регистрация → User сохранён, outbox enqueued
  2. Дубль email → EntityExistsException, save не вызван
  3. Пароль хешируется (BCrypt)
  4. Поля role=ROLE_USER, status=PENDING_VERIFICATION
  5. Outbox partitionKey = userId
- `notification/consumer/RegistrationNotificationConsumerTest.java` — 4 теста:
  1. Новая регистрация → sendWelcomeEmail + sendWelcomePush + Redis mark
  2. Redis dedup → пропуск, idempotentSkipped.increment()
  3. Email падает → push не вызывается, исключение поглощается
  4. Redis недоступен → fail-open, уведомления отправляются

---

## Полная flow регистрации (реализовано)

```
Client
  │  POST /api/v1/auth/register {email, password, fullName, phoneNumber}
  ▼
AuthController (authorization-service)
  │  @Valid → 400 если невалидно
  │  UserRegistrationService.registerUser()
  │    existsByEmail → 409 если дубль
  │    User(role=ROLE_USER, status=PENDING_VERIFICATION)
  │    userRepository.save()
  │    outboxHelper.enqueue("auth.user.registered")
  │  → 201 Created {userId, email, requiresEmailVerification=true}
  ▼
OutboxRelayScheduler → Kafka: auth.user.registered
  ├──▶ notification-service (RegistrationNotificationConsumer)
  │      sendWelcomeEmail(email, fullName, userId)
  │      sendWelcomePush(userId, fullName)
  │
  └──▶ (profile-service, account-service — будущие потребители)
```

---

## Актуальная Kafka-карта

```
auth.user.registered  → notification (welcome email + push)

payment-api → payment.created              → notification, report
payment-api → payment.status.changed       → notification, report
payment-api → payment.antifraud.check      → antifraud
antifraud   → payment.antifraud.response   → payment-api

transfer → transfer.antifraud.check        → antifraud
antifraud → transfer.antifraud.response    → transfer
transfer → transfer.events                 → history-service
transfer → transfer.notification           → notification

account → card.created/blocked/unblocked/limit.changed → notification
account ↔ authorization: auth.validate / auth.validate.response
audit.logs / error.logs → history-service
```

---

## Что нужно сделать в следующем чате

### 1. 🟡 P1 — Phase 3: Real Bank Notification Format (ВОПРОС 3)
`PaymentNotificationFormatter` в notification-service:
- Формат как у Беларусьбанка: `OPLATA 5000.00 RUB\nKARTA #2859\n16.04.2026 18:12\n...`
- Маскирование карты (последние 4 цифры)
- `formatPaymentPush(PaymentCreatedEvent, merchantId)`
- Обновить `NotificationConsumer.onPaymentCreated()` чтобы использовал formatter

### 2. 🟢 P2 — Финальный JaCoCo прогон
`mvn test` в `spring-boot-services/authorization` — убедиться что:
- `UserRegistrationServiceTest` — 5 тестов зелёные
- JaCoCo отчёт генерируется

### 3. 🟢 P2 — AuthControllerTest (интеграционный)
Добавить интеграционный тест `AuthControllerTest.java` в authorization:
- `POST /api/v1/auth/register` с валидными данными → 201
- Дубль email → 409
- Невалидный email → 400
- Слабый пароль (без спецсимвола) → 400

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн во всех Spring Boot сервисах
- **PostgreSQL**: Liquibase миграции (Spring Boot), Hibernate (Quarkus)
- **Redis**: notification-service (dedup + email cache + push token)
- **FCM**: Firebase Cloud Messaging
- **jjwt**: 0.11.5 (authorization, payment-api)
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---

## Как начать следующий чат

Приложи архив `xyzbank_v9_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V9.md внутри архива и продолжай работу.
Следующая задача: Phase 3 — PaymentNotificationFormatter (реальный банковский
формат push-уведомлений) из ВОПРОС 3.
```
