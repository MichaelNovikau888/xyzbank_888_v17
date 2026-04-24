# CONTINUATION_PROMPT_V15 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank.
Архив с кодом: `xyzbank_v15_complete.zip` (рабочая директория внутри: `xyzbank_work_v8/`).

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus 3.30.2**: history-service, notification-service, report-service, profile-service, public-info-quarkus
- **Kafka**: Outbox-паттерн (Spring Boot) / SmallRye Reactive Messaging (Quarkus)
- **PostgreSQL + Liquibase**, Redis, FCM, jjwt 0.11.5, JaCoCo 0.8.12
- **Тестирование**: JUnit 5, Mockito, AssertJ, RestAssured, H2 (тест-профиль Quarkus), Testcontainers (profile-service, antifraud)

---

## Что сделано в чатах 1–15 (полная история)

### Чаты 1–13 (см. CONTINUATION_PROMPT_V13.md внутри архива)
Antifraud, transfer, notification, JaCoCo, JWT, регистрация, PaymentNotificationFormatter,
red flags Kafka-топиков, AuthControllerTest, CDI-интерцепторы, полные тесты history/notification,
profile-service тесты, public-info продюсеры, antifraud outbox cleanup.

---

### Чат 14–15 — public-info, report-service, форматирование Javadoc

#### ✅ profile-service — откат Javadoc в Audit.java
Поля `entityType` и `operationType` в `profile/entity/Audit.java` возвращены к оригинальному виду.
Документация остаётся только в `EntityType` и `OperationType` enum (как задумывалось изначально).

#### ✅ public-info — AuditRepository: все методы задействованы
- `AuditService` расширен: `getByEntityType(String)` и `getAllByEntityJson(String)`
- `AuditServiceImpl` реализует оба метода через `AuditRepository.findByEntityType()` и `findAllByEntityJson()`
- `AuditResource` получил два новых эндпоинта:
  - `GET /api/public-info/audits/by-entity-type?type=ATMDto`
  - `GET /api/public-info/audits/by-entity-json?json=...`

#### ✅ public-info — BankDetailsRepository.findByBik() задействован
В `BankDetailsServiceImpl.create()` добавлена проверка уникальности BIK перед persist().
Дублирующий BIK → `ValidationException` (400). `findByBik()` больше не мёртвый метод.

#### ✅ public-info — полный комплект тестов (125 тестов в 8 файлах)

| Файл | Тип | Тестов |
|------|-----|--------|
| `ConsumersTest` | Unit | 28 |
| `ProducersTest` | Unit | 15 |
| `AuditServiceImplTest` | Unit | 13 |
| `BankDetailsServiceImplTest` | Unit | 13 |
| `PublicInfoResourceTest` | Интеграционный (H2) | 22 |
| `BankDetailsResourceTest` | Интеграционный (H2) | 14 |
| `BranchAndATMResourceTest` | Интеграционный (H2) | 9 |
| `BankDetailsServiceImplTest` (service/) | Unit | 11 |

#### ✅ AOP-анализ: CDI Interceptors vs Kafka (файл AOP_INTERCEPTOR_ANALYSIS.md)
- `@Auditable` + `AuditInterceptor` — аналог Spring AOP `@AfterReturning`
- **Конфликта с Kafka нет**: `@Incoming`-методы не проходят через CDI-прокси;
  но вызовы `service.*()` из консьюмеров через `@Inject` — проходят, аудит работает
- `@Blocking` и `@ActivateRequestContext` не влияют на CDI-перехват
- Единственная ловушка — self-invocation (`this.method()`) — в текущем коде отсутствует

#### ✅ report-service — полный рефакторинг

**Архитектура исправлена:**
- `@Incoming`-методы вынесены из `ReportService` в отдельный пакет `consumer/`
- `ReportService` теперь содержит только чистую бизнес-логику (SRP соблюдён)

**Новая структура пакетов:**
```
consumer/  ← PaymentReportConsumer, TransferReportConsumer  (@Incoming + @Blocking)
service/   ← ReportService  (бизнес-логика, периоды, CSV)
resource/  ← ReportResource (REST API — клиентский и бухгалтерский вид)
entity/    ← PaymentReport, TransferReport
event/     ← PaymentCreatedEvent, PaymentStatusChangedEvent, TransferNotificationEvent
dto/       ← PaymentReportDto, TransferReportDto, PeriodSummaryDto
```

**clientId → Long (= Profile.id):**
- В `PaymentReport` и `TransferReport` поле `clientId` изменено с `String` на `Long`
- Соответствует `Profile.id` из profile-service (типовая консистентность)

**TransferReport + TransferReportConsumer:**
- Новая сущность `TransferReport` для переводов (transfer.notification топик)
- `TransferReportConsumer` слушает `transfer.notification`
- UPSERT-стратегия: поддержка out-of-order событий (финальный статус без CREATED → создаёт запись)

**Два вида доступа в ReportResource:**
```
/client/{clientId}/payments/day      ← клиент: только свои платежи
/client/{clientId}/transfers/day     ← клиент: только свои переводы
/client/{clientId}/summary/day       ← клиент: сводка за день
/client/{clientId}/summary/week      ← клиент: сводка за неделю (формат 2026-W16)
/client/{clientId}/summary/month     ← клиент: сводка за месяц (формат 2026-04)
/bank/payments/day                   ← бухгалтерия: все платежи
/bank/transfers/day                  ← бухгалтерия: все переводы
/bank/summary/day|week|month         ← бухгалтерия: сводки по периодам
/bank/daily                          ← бухгалтерия: CSV (платежи + переводы)
/bank/partitions/health              ← здоровье партиций PostgreSQL
```

**Liquibase миграция `release-0.1.0.0/changelog-001.xml`:**
- `payment_reports` — партиционирована `PARTITION BY RANGE (report_date)`
- `transfer_reports` — аналогично
- Партиции на 3 месяца вперёд + DEFAULT-партиция для данных вне диапазона
- `kafka_idempotent_offset` — таблица для Exactly-Once (уникальный индекс на topic+partition+offset)

**Exactly-Once в Kafka (`application.properties`):**
```properties
mp.messaging.incoming.*.enable.auto.commit=false
mp.messaging.incoming.*.isolation.level=read_committed
```
Дополнительно: уникальные индексы на `payment_id` и `transfer_id` — второй уровень защиты.

**Тесты report-service (45 тестов в 4 файлах):**

| Файл | Тип | Тестов |
|------|-----|--------|
| `PaymentReportConsumerTest` | Unit + H2 | 9 |
| `TransferReportConsumerTest` | Unit + H2 | 9 |
| `ReportServiceTest` | Unit + H2 | 11 |
| `ReportResourceTest` | Интеграционный H2 | 16 |

**Удалены сталые файлы-заглушки:**
`ExampleResource.java`, `MyEntity.java`, `MyMessagingApplication.java`,
`ExampleResourceTest.java`, `ExampleResourceIT.java`, `MyMessagingApplicationTest.java`

#### ✅ Javadoc: исправлено форматирование во всём проекте

IDEA ругалась на пустые строки внутри Javadoc-блоков (` *` — строка только со звёздочкой).
Python-скрипт обработал **479 Java-файлов**, исправил **115** из них во всех сервисах.

Было (IDEA ругалась):
```java
/**
 * Первый абзац.
 *
 * Второй абзац.
 */
```

Стало (IDEA доволен):
```java
/**
 * Первый абзац.
 */
 /**
 * Второй абзац.
 */
```

---

## Итоговое покрытие тестами (778 тестов)

| Сервис | Тестов | Тип |
|--------|--------|-----|
| antifraud (Spring Boot) | 69 | Unit + Testcontainers |
| account (Spring Boot) | 94 | Unit + Integration |
| authorization (Spring Boot) | 48 | Unit + Integration |
| transfer (Spring Boot) | 39 | Unit + Integration |
| payment-api (Spring Boot) | 24 | Unit + Integration |
| history-service (Quarkus) | 94 | Unit + H2 Integration |
| notification-service (Quarkus) | 140 | Unit + H2 Integration |
| profile-service (Quarkus) | 60 | Unit + H2 + Testcontainers |
| public-info (Quarkus) | 125 | Unit + H2 Integration |
| report-service (Quarkus) | 45 | Unit + H2 Integration |
| **ИТОГО** | **778** | |

---

## Открытые задачи

### 🟡 P2 — JaCoCo финальный прогон Spring Boot сервисов
```bash
cd spring-boot-services
mvn test -pl account,antifraud,authorization,transfer,payment-api
# Убедиться что все тесты зелёные после всех рефакторингов
```

### 🟡 P2 — report-service: Liquibase не использует H2 в тест-профиле
В `%test` профиле `quarkus.liquibase.enabled=false`, а `hibernate.generation=drop-and-create`.
Таблицы `kafka_idempotent_offset` нет в H2 — это не критично для тестов (ORM создаёт payment_reports
и transfer_reports), но `kafka_idempotent_offset` в тестах не проверяется.
**Решение:** добавить H2-совместимый `import.sql` для тест-профиля с `CREATE TABLE kafka_idempotent_offset`.

### 🟡 P2 — report-service: replica datasource в тест-профиле
`checkPartitionHealth()` использует `dataSource` (primary), но `replicaDataSource` в H2
ссылается на тот же `testdb`. Для prod нужно убедиться что replica URL корректен.

### 🟢 P3 — profile-service: AuditService interface именование
`create(Object)` / `update(Object)` → `createAudit(T)` / `updateAudit(T)` для единообразия с public-info.

### 🟢 P3 — public-info: unit-тест AuditInterceptor через CDI
`@Auditable` активен на 5 ServiceImpl, но тест на реальный перехват через CDI-прокси отсутствует.
Написать `@QuarkusTest` тест: вызвать `service.create()` → проверить что `AuditService.createAudit()` вызван.

### 🟢 P3 — antifraud: финальный DROP outbox в следующем major-релизе
```xml
<!-- release-0.4.0.0/drop-outbox-events.xml -->
<dropTable tableName="outbox_events"/>
<dropIndex tableName="outbox_events" indexName="idx_antifraud_outbox_pending"/>
```

### 🟢 P3 — report-service: партиции нужно создавать автоматически
Сейчас партиции прописаны на фиксированные месяцы (2026-04, 05, 06).
**Решение:** написать `@Scheduled` job или Liquibase precondition, который создаёт партиции
на N месяцев вперёд при старте сервиса.

### 🟢 P3 — report-service: авторизация на уровне resource
`/client/{clientId}/...` — сейчас любой может запросить данные любого clientId.
**Решение:** валидировать JWT-токен (из заголовка Authorization) и сравнивать `sub` с `clientId`.
Для бухгалтерских эндпоинтов `/bank/...` — проверять роль `ACCOUNTANT` в JWT.

### 🟢 P3 — Swagger/OpenAPI документация
Все сервисы имеют `quarkus.swagger-ui.always-include=true`, но `@Operation` аннотации
есть только в report-service и public-info. Добавить в history-service, notification-service,
profile-service.

---

## Как начать следующий чат

Приложи архив `xyzbank_v15_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V15.md внутри архива и продолжай работу.
Следующая задача: [выбери из списка открытых задач выше]
```

Рекомендуемый порядок:
1. JaCoCo финальный прогон (P2) — убедиться что всё компилируется
2. report-service партиции автоматически (P3)
3. report-service авторизация JWT (P3)
4. AuditInterceptor CDI тест (P3)
