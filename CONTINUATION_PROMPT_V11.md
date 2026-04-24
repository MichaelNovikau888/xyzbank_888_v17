# CONTINUATION_PROMPT_V11 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v11)

### Чаты 1–10 (см. CONTINUATION_PROMPT_V10.md)
<вся история — antifraud, transfer, notification, JaCoCo, REVIEW статус,
Phase 1 Security Fix (JWT), Phase 2 User Registration Flow, Phase 3 PaymentNotificationFormatter.
Red flags: accountNumber Long→String в 10 файлах account-service. Подробности в V10.md>

### Чат 11 — Red flag исправления (profile-service, public-info, notification-service) + AuthControllerTest

#### Red flags ✅ исправлены

| Файл | Проблема | Исправление |
|------|----------|-------------|
| `profile-service-quarkus/src/main/java/.../service/ProfileServiceImpl.java` | Дублирующий черновик сервиса без интерфейса, рядом с реальным `service/impl/ProfileServiceImpl.java` | **Удалён** |
| `public-info-quarkus/src/main/resources/db/changelog/db.changelog-master.yaml` | Второй `- include:` вне `databaseChangeLog:` (неверный отступ YAML) | Исправлен отступ |
| `profile-service-quarkus/src/main/resources/db/changelog/db.changelog-master.yaml` | Та же проблема с YAML-отступом | Исправлен отступ |
| `ProfileResourceTest.java` | `p.setPhoneNumber(79001234567L)` — `long` вместо `String` | `"79001234567"` |
| `ProfileServiceImplTest.java` | `p.setPhoneNumber(79001234567L)` и `dto.setPhoneNumber(79001111111L)` | `"79001234567"`, `"79001111111"` |
| `BranchAndATMResourceTest.java` | `b.setPhoneNumber(phone)` — `long` параметр метода | `String.valueOf(phone)` |
| `NotificationConsumerTest.java` | `sendPaymentStatusChanged` вызывался с 6 аргументами вместо 7 | Добавлен `any()` для `reason` |
| `notification-service/pom.xml` | Отсутствовали Lombok, `quarkus-smallrye-fault-tolerance`, AssertJ | Добавлены зависимости + `annotationProcessorPaths` для Lombok |

#### ✅ AuthControllerTest создан

Файл: `spring-boot-services/authorization/src/test/java/com/bank/authorization/controller/AuthControllerTest.java`

Паттерн: `@AutoConfigureMockMvc` + `extends AbstractIntegrationTest` (Testcontainers PostgreSQL + Kafka).

5 тест-сценариев:
1. Валидные данные → 201 Created (userId, email, requiresEmailVerification=true)
2. Дублирующий email → 409 Conflict
3. Невалидный email → 400 Bad Request
4. Пароль без спецсимвола → 400 Bad Request
5. Пароль < 8 символов → 400 Bad Request

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн во всех Spring Boot сервисах
- **PostgreSQL**: Liquibase (Spring Boot), Hibernate (Quarkus)
- **Redis**: notification-service (dedup TTL=24h, email cache TTL=1h, push token TTL=30d)
- **FCM**: Firebase Cloud Messaging
- **jjwt**: 0.11.5 (authorization, payment-api)
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---


### Чат 11 (продолжение) — Red flags: antifraud, transfer, authorization

#### Red flags ✅ исправлены

| Файл | Проблема | Исправление |
|------|----------|-------------|
| `UserRegistrationService.java` | `user.setStatus(UserStatus.PENDING_VERIFICATION)` — enum вместо String | `.name()` добавлен |
| `SuspiciousTransferProducerTest.java` | `event.setDecision(FraudDecision.X)` — enum вместо String | `.name()` добавлен |
| `SuspiciousTransferConsumerTest.java` | Тест написан под устаревший API (`listenTransfer`, `eventResponse`, map-based) — методов не существует | Полная перезапись под реальный API: `handlePaymentAntifraudCheck(AntifraudRequestEvent)` + `handleTransferAntifraudCheck(TransferAntifraudRequestEvent)`, 8 тест-сценариев |
| `AuditServiceImplTest.java` (transfer) | `@Mock TransferProducer` — класса не существует; тест верифицировал `sendAuditHistory()` и `KafkaErrorPublisher` — не являются зависимостями реального `AuditServiceImpl` | Полная перезапись: `@Mock HistoryOutboxHelper`, верификация `enqueueTransferEvent()`, убран `KafkaErrorPublisher` |
| `com.bank.transfer.kafka` (пакет) | Пустая директория — артефакт рефакторинга | Удалена |


### Чат 11 (финал) — PaymentNotificationFormatter v2 + финальный скан

#### ✅ PaymentNotificationFormatter v2: cardLastFour из события

**`payment-api/PaymentCreatedEvent.java`** — добавлено поле `cardLastFour` (String, optional).
Полный конструктор (8 арг) + 7-арг делегирует в него с `null`.
Геттер/сеттер добавлены.

**`PaymentService.savePaymentCreatedToOutbox()`** — добавлен вызов `extractLastFour(payment.getRecipientAccount())`,
результат передаётся как `cardLastFour` в событие. Добавлен приватный helper `extractLastFour(String)`.

**`notification-service/PaymentCreatedEvent.java`** — добавлено публичное поле `cardLastFour`.
Jackson десериализует его автоматически из JSON.

**`NotificationConsumer.handlePaymentCreated()`** — `null` заменён на `event.cardLastFour`.
Теперь push-уведомление показывает реальные последние 4 цифры счёта (например «KARTA #4321»)
вместо «KARTA #****».

#### ✅ Финальный скан red flags — чисто
Полный grep по всем сервисам: `Cannot resolve`, `cannot be applied`, `red flag` — **0 результатов**.

## Открытые задачи (P2/P3)

### ✅ DONE — PaymentNotificationFormatter v2: cardLastFour реализован

### 🟢 P2 — JaCoCo финальный прогон
`mvn test` в каждом Spring Boot сервисе — убедиться что все тесты зелёные.
Можно запустить из `spring-boot-services/`: `mvn test -pl account,antifraud,authorization,transfer,payment-api`.

### 🟢 P3 — Прочие red flags если появятся

---

## Как начать следующий чат

Приложи архив `xyzbank_v11_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V11.md внутри архива и продолжай работу.
Следующая задача: PaymentNotificationFormatter v2 (cardNumber из события) +
JaCoCo финальный прогон account-service.
```
