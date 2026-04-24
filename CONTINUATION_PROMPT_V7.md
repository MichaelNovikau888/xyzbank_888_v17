# CONTINUATION_PROMPT_V7 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v7)

### Чаты 1–6 (см. CONTINUATION_PROMPT_V6.md)
<сохранена вся история — antifraud, transfer, notification, docker-compose,
dead topics cleanup, DTO validation, ClientService, Qodana fixes. Подробности в V6.md>

### Чат 7 — JaCoCo, REVIEW статус, AuditService audit, ClientServiceTest

#### Задача 1 ✅: JaCoCo в spring-boot-services/pom.xml
Добавлен `jacoco-maven-plugin` версии 0.8.12 в родительский POM Spring Boot сервисов:
- `prepare-agent` — инструментирует байт-код перед тестами
- `report` (phase=test) — генерирует `target/site/jacoco/index.html`
- `check` (minimum=0.00) — не блокирует сборку, но проверяет наличие данных
- `<excludes>` — исключены `*Application`, `**/dto/**`, `**/entity/**`,
  `**/*MapperImpl.class`, `**/config/**`, `**/exception/**`, `**/enums/**`

Запуск: `mvn test` в любом Spring Boot модуле → отчёт в `target/site/jacoco/index.html`.

#### Задача 2 ✅: REVIEW статус в TransferNotificationConsumer
`TransferNotificationConsumer.java` — добавлен `case "REVIEW"`:
- Вызывает `pushService.sendTransferReviewPush(clientId, transferId, amount, currency)`
- Email НЕ отправляется (промежуточный статус антифрод-проверки)
- В БД НЕ пишется (`saveIfFinal` игнорирует REVIEW, не финальный статус)

`PushNotificationService.java` — добавлен метод `sendTransferReviewPush()`:
- Заголовок: `"🔍 Перевод на проверке"`
- Тело: `"Перевод №{id} на сумму {X} RUB отправлен на проверку — ожидайте до 24 часов"`
- `getTransferFinalTitle()` — добавлен кейс `"REVIEW"` для защиты от fallback

Javadoc потребителя обновлён — REVIEW статус задокументирован.

#### Задача 3 ✅: AuditServiceImpl (transfer) — проверка legacy
**Вывод:** `AuditService` интерфейс и `AuditServiceImpl` **нигде не вызываются** из
продуктового кода кроме тестов. Реальный аудит идёт:
`TransferServiceImpl → AntifraudOutboxHelper / NotificationOutboxHelper → Outbox → Kafka → history-service`

Добавлен подробный Javadoc в `AuditService.java`:
- Документирует, что `auditXxxTransfer()` методы — legacy без продуктовых вызовов
- `getAuditHistory()` — используется REST-эндпоинтом `AuditController`
- TODO: рассмотреть удаление или упрощение при следующем рефакторинге

#### Задача 4 ✅: ClientServiceTest.java (Quarkus, 7 тестов)
Новый тест: `notification-service/src/test/java/.../service/ClientServiceTest.java`

Покрытые сценарии:
1. Email в Redis → REST не вызывается, возвращается кэшированный email
2. Redis cache miss → REST вызван → результат кэшируется `setex(key, 3600, email)`
3. REST недоступен (исключение) → null без пробрасывания исключения
4. clientId не числовой (`"user-abc-123"`) → null без вызова REST
5. clientId=null → null, Redis и REST не вызываются
6. clientId="" (blank) → null, Redis и REST не вызываются
7. Redis недоступен → fail-open, REST вызывается, email возвращается

Техническое решение: добавлен package-private метод `ClientService.initForTest(redis)`
для подмены Redis-команд в тестах после `@PostConstruct`.

#### Задача 5 ✅: TransferNotificationConsumerTest — REVIEW кейс
Добавлены 2 теста в существующий файл:
- `REVIEW → sendTransferReviewPush вызван, email и DB НЕ вызываются`
- `REVIEW: дубль Redis → push НЕ вызывается`

---

## Актуальная Kafka-карта (без изменений после чата 6)

```
payment-api → payment.created              → notification (push+email), report (CSV)
payment-api → payment.status.changed       → notification, report
payment-api → payment.antifraud.check      → antifraud
antifraud   → payment.antifraud.response   → payment-api
payment-api → account.events               → history-service

transfer → transfer.antifraud.check        → antifraud
antifraud → transfer.antifraud.response    → transfer
transfer → transfer.events                 → history-service (через HistoryOutboxHelper)
transfer → transfer.notification           → notification (через TransferNotificationOutboxHelper)

account → card.created/blocked/unblocked/limit.changed → notification

account ↔ authorization: account.create/update/delete/get/getById + external.*
account ↔ authorization: auth.validate / auth.validate.response

audit.logs  → history-service  (producers: antifraud, account, profile)
error.logs  → history-service  (producers: transfer, authorization, profile)
```

---

## Статус Kafka топиков по статусам перевода
| Статус   | Push                    | Email      | DB запись    |
|----------|-------------------------|------------|--------------|
| CREATED  | sendTransferCreatedPush | ❌         | ❌           |
| REVIEW   | sendTransferReviewPush  | ❌         | ❌           |
| COMPLETED| sendTransferFinalPush   | ✅ подробный| ✅ финальный |
| BLOCKED  | sendTransferFinalPush   | ✅ с reason | ✅ финальный |
| CANCELLED| sendTransferFinalPush   | ✅ с reason | ✅ финальный |

---

## Что нужно сделать в следующем чате

### 1. 🔴 P0 — Qodana: запустить и получить актуальный список
После всех правок чатов 1–7 количество issues должно снизиться до ≤50.
Запустить:
```bash
docker run --rm -it \
  -v $(pwd):/data/project \
  jetbrains/qodana-jvm \
  --save-report
```

### 2. 🟡 P1 — AntifraudResponseConsumer (transfer): добавить обработку REVIEW
`AntifraudResponseConsumer` в transfer-service получает ответ антифрода.
При статусе `REVIEW` нужно:
- Обновить статус перевода в БД → `REVIEW`
- Вызвать `notificationOutboxHelper.enqueueReview(...)` → топик `transfer.notification`
- Уведомление доставит `TransferNotificationConsumer` (уже готов в чате 7)

### 3. 🟡 P1 — TransferNotificationOutboxHelper: метод enqueueReview()
Добавить `enqueueReview(transferId, clientId, amount, currency)` в
`TransferNotificationOutboxHelper.java` по аналогии с `enqueueCreated()`.
Статус в событии: `"REVIEW"`, reason: `null`.

### 4. 🟡 P1 — Покрытие тестами AntifraudResponseConsumer (transfer)
Добавить тест на REVIEW кейс в `AntifraudResponseConsumerTest`.

### 5. 🟢 P2 — Финальная JaCoCo проверка
После всех исправлений запустить `mvn test` в каждом Spring Boot модуле,
убедиться что отчёты генерируются корректно.

### 6. ℹ️ P2 — spring.cloud.kubernetes.* (намеренно оставлено)
Qodana даёт 6 warnings — допустимо, задел на K8s-деплой.

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн (OutboxRelayScheduler) во всех Spring Boot сервисах
- **PostgreSQL**: каждый сервис своя схема; Liquibase для Spring Boot, Hibernate для Quarkus
- **Redis**: notification-service (dedup TTL=24h + email cache TTL=1h + push token TTL=30d)
- **FCM**: Firebase Cloud Messaging для push-уведомлений
- **Swagger**: springdoc-openapi 2.3.0
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---

## Как начать следующий чат

Приложи архив `xyzbank_v7_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V7.md внутри архива и продолжай работу.
Следующая задача: добавить REVIEW обработку в AntifraudResponseConsumer (transfer),
добавить enqueueReview() в TransferNotificationOutboxHelper.
```
