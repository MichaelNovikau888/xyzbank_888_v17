# CONTINUATION_PROMPT_V13 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v13)

### Чаты 1–12 (см. CONTINUATION_PROMPT_V12.md)
<полная история — antifraud, transfer, notification, JaCoCo, JWT, регистрация, форматтер,
red flags, AuthControllerTest, PaymentNotificationFormatter v2, CDI-интерцепторы,
Kafka-архитектура, history-service тесты, notification-service тесты. Подробности в V12.md>

---

### Чат 13 — entityType/operationType, profile-service тесты, public-info продюсеры, antifraud cleanup

#### ✅ Audit.java — документированы entityType и operationType
Оба поля получили Javadoc с объяснением:
- `entityType`: значения из `EntityType` enum — "Profile", "Passport", "AccountDetails", etc.
  Устанавливается автоматически через `AuditInterceptor` по `dto.getClass().getSimpleName()`.
- `operationType`: значения "Create" / "Update" из `OperationType` enum.
  Задаётся параметром `@Auditable(operation="UPDATE")` или дефолтом "Create".

#### ✅ profile-service — новые тесты (60 тестов в 6 файлах)

| Файл | Тип | Тестов | Покрытие |
|------|-----|--------|----------|
| `ProfileIntegrationTest` | **Интеграционный** (H2 in-memory) | 14 | Полный HTTP: GET/POST/PUT/DELETE, 400/404/409, health, metrics |
| `AuditServiceImplTest` | Unit | 6 | create(): entityType/operationType/entityJson; update(): найдено/не найдено/без id |
| `AccountDetailsConsumerTest` | Unit | 8 | Идемпотентность create/update/delete/get для AccountDetails |

Добавлен Testcontainers в `pom.xml` (quarkus-test-postgresql + testcontainers 1.19.7).

#### ✅ notification-service — полное покрытие тестами (140 тестов в 11 файлах)

Добавлены новые unit-тесты:
- `EmailServiceTest` (15): sendWelcomeEmail, Redis cache hit/miss, плейсхолдеры, sendTransferFinalNotification
- `NotificationRecordServiceTest` (17): saveIfFinal все 4 финальных статуса / нефинальные / null; isFinalStatus
- `PushNotificationServiceTest` (20): все send* методы, registerPushToken TTL=30д, Redis down → graceful

Новые интеграционные тесты:
- `NotificationResourceTest` (10): GET /api/v1/notifications/{clientId}, пагинация, фильтрация, 400
- `PushTokenResourceTest` расширен (11): 200 android/ios, 400 всех полей, health, metrics ×3

#### ✅ history-service — полное покрытие тестами (94 теста в 5 файлах)
`HistoryServiceImplTest`, `HistoryResourceTest`, `HistoryKafkaListenerTest`,
`HistoryMapperTest`, `HistoryRepositoryTest`. Подробности в V12.md.

#### ✅ public-info — пустые продюсеры реализованы

5 продюсеров (`ATM`, `Branch`, `Certificate`, `License`, `BankDetails`) переписаны
с заглушки `LOG.infof` на настоящую отправку через SmallRye `Emitter<String>`:
- `sendCreated(dto)` → JSON-envelope `{"event":"CREATED","data":{...}}`
- `sendUpdated(dto)` → JSON-envelope `{"event":"UPDATED","data":{...}}`
- `sendDeleted(id)` → `{"event":"DELETED","id":N}`

5 ServiceImpl подключены к продюсерам: вызов после каждой мутации.
5 новых outgoing каналов в `application.properties` (+ тест-профиль in-memory).

#### ✅ antifraud Outbox — cleanup завершён

**Liquibase migration release-0.3.0.0** (`deprecate-outbox-events.xml`):
- `setTableRemarks` на таблицу `outbox_events` — помечена как DEPRECATED
- Таблица не удалена (обратная совместимость), но в следующем major-релизе будет DROP TABLE
- Master changelog обновлён

**`AntifraudAnalysisIntegrationTest`** переписан:
- Удалены все assertions на `outboxRepository` (OutboxHelper больше не пишет)
- Удалены импорты `OutboxEvent`, `OutboxRepository`
- Удалён тест 6 (OutboxRelayScheduler → Kafka) — заменён на тест граничного значения блокировки
- Тест 11 (Liquibase tables exist) сохранён с комментарием об outbox
- Добавлены тесты 10 (REVIEW на граничном значении 50 000) — итого 11 тестов

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн во всех Spring Boot сервисах; SmallRye Reactive Messaging в Quarkus
- **PostgreSQL**: Liquibase (Spring Boot), Hibernate (Quarkus)
- **Redis**: notification-service (dedup TTL=24h, email cache TTL=1h, push token TTL=30d)
- **FCM**: Firebase Cloud Messaging
- **jjwt**: 0.11.5 (authorization, payment-api)
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---

## Открытые задачи (P2/P3)

### 🟡 P2 — JaCoCo финальный прогон
```bash
cd spring-boot-services && mvn test -pl account,antifraud,authorization,transfer,payment-api
```
Убедиться что все тесты зелёные после всех рефакторингов.

### 🟢 P3 — profile-service AuditService interface
Имена `create(Object)` / `update(Object)` → `createAudit(T)` / `updateAudit(T)` для единообразия с public-info.

### 🟢 P3 — public-info AuditInterceptor: тесты
`@Auditable` теперь активен на 5 ServiceImpl, но unit-тестов на перехват нет.
Добавить через CDI-контекст (@QuarkusTest).

### 🟢 P3 — antifraud Outbox: финальный DROP в следующем major
```xml
<!-- release-0.4.0.0/drop-outbox-events.xml -->
<dropTable tableName="outbox_events"/>
<dropIndex tableName="outbox_events" indexName="idx_antifraud_outbox_pending"/>
```

### 🟢 P3 — public-info: тесты для новых продюсеров
Unit-тесты на `ATMProducer`, `BranchProducer` и т.д. — проверить что Emitter вызывается.

---

## Как начать следующий чат

Приложи архив `xyzbank_v13_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V13.md внутри архива и продолжай работу.
Следующая задача: JaCoCo финальный прогон Spring Boot сервисов.
```
