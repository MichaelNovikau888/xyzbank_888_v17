# CONTINUATION_PROMPT_V16 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank.
Архив с кодом: `xyzbank_v16_complete.zip` (рабочая директория внутри: `xyzbank_work_v8/`).

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus 3.30.2**: history-service, notification-service, report-service, profile-service, public-info-quarkus
- **Kafka**: Outbox-паттерн (Spring Boot) / SmallRye Reactive Messaging (Quarkus)
- **PostgreSQL + Liquibase**, Redis, FCM, jjwt 0.11.5, JaCoCo 0.8.12
- **Тестирование**: JUnit 5, Mockito, AssertJ, RestAssured, H2 (тест-профиль Quarkus), Testcontainers (profile-service, antifraud)

---

## Что сделано в чатах 1–16 (полная история)

### Чаты 1–15 (см. CONTINUATION_PROMPT_V15.md внутри архива)
Antifraud, transfer, notification, JaCoCo, JWT, история, профиль, public-info,
report-service рефакторинг, Liquibase партиционирование, Exactly-Once Kafka,
Javadoc форматирование (479 файлов).

---

### Чат 16 — report-service: import.sql, PartitionScheduler, JWT-авторизация; public-info: CDI-тест

#### ✅ report-service: H2-совместимый import.sql

Файл `report-service/src/main/resources/import.sql` заменён.
Содержит DDL для `kafka_idempotent_offset` в H2-совместимом синтаксисе:
- `BIGINT AUTO_INCREMENT` вместо `BIGSERIAL` (H2 не поддерживает PostgreSQL-синтаксис)
- `CONSTRAINT uq_kafka_offset UNIQUE` — идемпотентная защита сохранена
- Подробный комментарий почему файл нужен (Liquibase отключён в тест-профиле)

#### ✅ report-service: PartitionScheduler — автосоздание партиций

Новый бин `com.bank.report.scheduler.PartitionScheduler`:
- `@Scheduled(cron = "0 5 0 1 * ?")` — ежемесячное задание (1-е число, 00:05)
- `onStart(@Observes StartupEvent)` — создание при старте приложения
- `ensurePartitions()` — создаёт партиции на `MONTHS_AHEAD=3` месяца вперёд
- `createPartitionIfAbsent()` — идемпотентно (IF NOT EXISTS + проверка pg_tables)
- `partitionExists()` — проверка через pg_tables (без side-effects при уже существующей)
- Обе таблицы: `payment_reports` и `transfer_reports`
- **Тест-профиль**: `%test.quarkus.scheduler.enabled=false` — H2 не поддерживает pg_tables

Зависимость добавлена в pom.xml:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-scheduler</artifactId>
</dependency>
```

Тесты: `PartitionSchedulerTest` (5 тестов) — через мок DataSource.

#### ✅ report-service: JWT-авторизация на эндпоинтах

**Новый класс** `com.bank.report.security.JwtUtil`:
- `extractClientId(authHeader)` → Long (claim `clientId` или fallback на `sub`)
- `extractRoles(authHeader)` → `List<String>` из claim `authorities`
- `hasRole(authHeader, role)` → boolean
- `hasBearerToken(authHeader)` → boolean (без парсинга)
- Структура токена совпадает с authorization-service (jjwt 0.11.5)

**Обновлён ReportResource** — добавлены JWT-проверки:
- `/client/{clientId}/...` — `ROLE_USER` может видеть только свой clientId; `ROLE_ADMIN` — любой
- `/bank/...` — только `ROLE_ADMIN`
- 401 при отсутствии/невалидном токене; 403 при недостаточных правах
- Флаг `app.jwt.auth-enabled` (default=true) — в тест-профиле `false`

**pom.xml** — добавлены jjwt-зависимости (0.11.5):
```xml
<dependency>jjwt-api</dependency>       <!-- compile -->
<dependency>jjwt-impl</dependency>      <!-- runtime -->
<dependency>jjwt-jackson</dependency>   <!-- runtime -->
```

**application.properties** — новые свойства:
```properties
app.jwt.secret-key=${JWT_SECRET_KEY:...base64-default...}
app.jwt.auth-enabled=true

%test.app.jwt.auth-enabled=false
%test.app.jwt.secret-key=dGVzdC1zZWNyZXQta2V5...
```

**Новые тесты:**
- `JwtUtilTest` (15 тестов) — `@QuarkusTest`, генерация токенов через jjwt
- `ReportResourceAuthTest` (16 тестов) — `@QuarkusTest` + `@TestProfile(AuthEnabledProfile)`,
  проверяет 401/403/200 на всех эндпоинтах

#### ✅ public-info: CDI-тест AuditInterceptor

Новый файл `AuditInterceptorCdiTest` (6 тестов):
- `@QuarkusTest` — реальный CDI-контейнер, реальный прокси, перехватчик активен
- `@Inject BankDetailsService` — через CDI-прокси, `@Auditable` перехватывается
- `@Inject AuditRepository` — читаем реальную H2-таблицу для проверки
- `@InjectMock AuditProducer`, `BankDetailsProducer` — Kafka не нужен

| Тест | Проверяет |
|------|-----------|
| `create_triggersCreateAudit` | `operationType=CREATE`, `entityType` содержит `BankDetails`, `entityJson` содержит BIK |
| `update_triggersUpdateAudit` | `operationType=UPDATE`, `entityJson` содержит новый город |
| `delete_doesNotTriggerAudit` | `deleteById()` без `@Auditable` → аудит не создаётся |
| `nullReturnValue_doesNotAudit` | `if (result != null)` guard в интерцепторе |
| `create_callsAuditProducer` | `AuditProducer.sendAudit()` вызывается после create() |
| `update_callsAuditProducer` | `sendAudit()` вызывается с `operationType=UPDATE` |

---

## Итоговое покрытие тестами (после чата 16: ~836 тестов)

| Сервис | Тестов | Изменение |
|--------|--------|-----------|
| antifraud (Spring Boot) | 69 | — |
| account (Spring Boot) | 94 | — |
| authorization (Spring Boot) | 48 | — |
| transfer (Spring Boot) | 39 | — |
| payment-api (Spring Boot) | 24 | — |
| history-service (Quarkus) | 94 | — |
| notification-service (Quarkus) | 140 | — |
| profile-service (Quarkus) | 60 | — |
| public-info (Quarkus) | 131 | +6 (AuditInterceptorCdiTest) |
| report-service (Quarkus) | 81 | +36 (PartitionSchedulerTest +5, JwtUtilTest +15, ReportResourceAuthTest +16) |
| **ИТОГО** | **~836** | **+58** |

---

## Открытые задачи

### 🟡 P2 — JaCoCo финальный прогон Spring Boot сервисов
```bash
cd spring-boot-services
mvn test -pl account,antifraud,authorization,transfer,payment-api
# Убедиться что все тесты зелёные после всех рефакторингов
```

### 🟡 P2 — report-service: replica datasource в тест-профиле
`checkPartitionHealth()` использует `dataSource` (primary), но `replicaDataSource`
в H2 ссылается на тот же `testdb`. Для prod нужно убедиться что replica URL корректен.
Если используется database.replication.mode — документировать.

### 🟢 P3 — profile-service: AuditService interface именование
`create(Object)` / `update(Object)` → `createAudit(T)` / `updateAudit(T)`
для единообразия с public-info.

### 🟢 P3 — antifraud: финальный DROP outbox в следующем major-релизе
```xml
<!-- release-0.4.0.0/drop-outbox-events.xml -->
<dropTable tableName="outbox_events"/>
<dropIndex tableName="outbox_events" indexName="idx_antifraud_outbox_pending"/>
```

### 🟢 P3 — Swagger/OpenAPI документация
Все сервисы имеют `quarkus.swagger-ui.always-include=true`, но `@Operation`
есть только в report-service и public-info. Добавить в history-service,
notification-service, profile-service.

### 🟢 P3 — report-service: PartitionSchedulerTest улучшение
Текущие тесты используют мок DataSource. Можно добавить
интеграционный тест с реальным H2 + SQL `SHOW TABLES` для проверки
что H2 корректно обрабатывает отсутствующие партиции.

---

## Как начать следующий чат

Приложи архив `xyzbank_v16_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V16.md внутри архива и продолжай работу.
Следующая задача: JaCoCo финальный прогон Spring Boot сервисов.
```

Рекомендуемый порядок:
1. JaCoCo финальный прогон (P2)
2. Swagger/OpenAPI документация (P3)
3. profile-service AuditService именование (P3)
