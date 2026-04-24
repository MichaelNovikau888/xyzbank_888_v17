# CONTINUATION_PROMPT_V8 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v8)

### Чаты 1–7 (см. CONTINUATION_PROMPT_V7.md)
<вся история — antifraud, transfer, notification, docker-compose, dead topics,
DTO validation, ClientService, JaCoCo, REVIEW push, AuditService, ClientServiceTest,
TransferNotificationConsumerTest, AntifraudResponseConsumer REVIEW. Подробности в V7.md>

### Чат 8 — Phase 1 Security Fix: JWT вместо Client-Id header (payment-api)

#### Задача 1 ✅: jjwt зависимости в payment-api/pom.xml
Добавлены `jjwt-api`, `jjwt-impl`, `jjwt-jackson` версии `0.11.5` (та же что в authorization).

#### Задача 2 ✅: JWT secret в конфигах payment-api
- `application.yml` → `app.jwt.secret-key: bmF3RUZlWVlLUFR4akRaT0YrZW9lcG1QemErQ0xoSmQrZzltM0dIY3Zyb0E=`
- `application-test.yml` → `app.jwt.secret-key: dGVzdC1zZWNyZXQta2V5LWF0LWxlYXN0LTMyLWJ5dGVzISE=`

#### Задача 3 ✅: JwtUtil.java (новый, payment-api/security/)
- `extractClientId(authHeader)` — парсит `Bearer <token>`, возвращает claim `clientId` или fallback на `subject`
- `hasBearerToken(authHeader)` — проверка наличия Bearer
- Все ошибки (нет заголовка, нет Bearer, истёк, неверная подпись) → `JwtException`

#### Задача 4 ✅: PaymentController переписан
- `@RequestHeader("Client-Id")` → `@RequestHeader("Authorization")`
- clientId извлекается через `jwtUtil.extractClientId(authHeader)`
- При ошибке JWT → HTTP 401
- Javadoc обновлён

#### Задача 5 ✅: JwtTokenUtil.extractClientId() в authorization-service
Добавлен метод в `authorization/utils/JwtTokenUtil.java`:
- Разбирает `Bearer <token>`, извлекает claim `clientId` или fallback на subject
- Javadoc: для USER clientId == userId; для корпоративных — clientId == companyId

#### Задача 6 ✅: JwtUtilTest.java (payment-api, 9 тестов)
`payment-api/src/test/java/.../security/JwtUtilTest.java`:
1. Валидный токен с claim clientId → возвращает clientId
2. Токен без claim clientId → fallback на subject
3. Просроченный токен → JwtException
4. Неверная подпись → JwtException
5. Заголовок без «Bearer » → JwtException
6. Пустой заголовок → JwtException
7. null заголовок → JwtException
8a. hasBearerToken: с Bearer → true
8b. hasBearerToken: без Bearer / null / Basic → false

#### Задача 7 ✅: PaymentIntegrationTest обновлён
- `buildRequest()` теперь ставит `Authorization: Bearer <jwt>` вместо `Client-Id`
- Добавлен вспомогательный метод `buildJwt(clientId)` — генерирует тестовый токен
- Тест 5 (GET by-key) обновлён: JWT вместо Client-Id
- Добавлен **Тест 6**: POST без Authorization header → 401

#### Анализ transfer-service: изменений не требуется ✅
Transfer не имеет REST-контроллера. clientId приходит в DTO через Kafka
(отправляется account-service). Это правильная архитектура — менять нечего.

---

## Статус Security Fix (Phase 1 из ВОПРОС 3)

| Сервис | Client-Id header | Статус |
|--------|-----------------|--------|
| payment-api | Убран → JWT | ✅ Готово |
| transfer | Нет HTTP-контроллера | ✅ N/A |
| authorization | Уже на JWT | ✅ N/A |
| profile-service (Quarkus) | Нужно проверить | ⚠️ Следующий чат |

---

## Актуальная Kafka-карта (без изменений)

```
payment-api → payment.created              → notification (push+email), report
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

### 1. 🟡 P1 — Phase 2: User Registration Flow (из ВОПРОС 3, Task 2.1–2.4)

**Task 2.1:** `UserRegistrationRequest` DTO + валидация в authorization-service:
- `@Email`, `@NotBlank`, `@Size`, `@Pattern` на password (upper+lower+digit+special)
- `UserRegistrationResponse`

**Task 2.2:** `UserRegistrationService.registerUser()` в authorization-service:
- Проверка уникальности email
- Создание `User(role=USER, status=PENDING_VERIFICATION)`
- `outboxHelper.enqueueUserRegistered()` → топик `auth.user.registered`

**Task 2.3:** `AuthController.register()` эндпоинт:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify-email?token=...`

**Task 2.4:** Consumers в других сервисах на топик `auth.user.registered`:
- `notification-service`: welcome email + push
- (profile и account уже создают данные по-другому в текущей архитектуре)

### 2. 🟢 P2 — profile-service: проверить наличие Client-Id header
Если найдены — заменить на JWT аналогично payment-api.

### 3. 🟢 P2 — JaCoCo: убедиться что тесты проходят
`mvn test` в payment-api модуле после всех изменений.

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн во всех Spring Boot сервисах
- **PostgreSQL**: каждый сервис своя схема
- **Redis**: notification-service (dedup + email cache + push token)
- **FCM**: Firebase Cloud Messaging
- **jjwt**: 0.11.5 (authorization, payment-api), 0.9.1 (transfer — legacy)
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---

## Как начать следующий чат

Приложи архив `xyzbank_v8_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V8.md внутри архива и продолжай работу.
Следующая задача: Phase 2 User Registration Flow из ВОПРОС 3 —
UserRegistrationRequest DTO, UserRegistrationService, AuthController.register().
```
