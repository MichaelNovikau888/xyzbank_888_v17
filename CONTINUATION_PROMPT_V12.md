# CONTINUATION_PROMPT_V12 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v12)

### Чаты 1–11 (см. CONTINUATION_PROMPT_V11.md)
<вся история — antifraud, transfer, notification, JaCoCo, REVIEW статус,
Phase 1 Security Fix (JWT), Phase 2 User Registration Flow, Phase 3 PaymentNotificationFormatter,
Red flags (phoneNumber, YAML changelogs, NotificationConsumerTest, pom.xml), AuthControllerTest,
PaymentNotificationFormatter v2 (cardLastFour). Подробности в V11.md>

---

### Чат 12 — CDI Interceptor аудит (public-info) + Kafka-архитектура (коллега-промпт)

---

## ✅ Исправление CDI-перехватчиков (public-info-service)

### Проблема 1 (🔴 P0): @Auditable не навешан ни на один метод в public-info
`AuditInterceptor` был зарегистрирован, но в 5 ServiceImpl (`ATM`, `BankDetails`, `Branch`,
`Certificate`, `License`) ни один `create()` / `update()` не был помечен аннотацией.
Аудит в public-info не работал вообще.

**Исправление:** добавлены аннотации во все 5 файлов:
- `ATMServiceImpl.create()` → `@Auditable`
- `ATMServiceImpl.update()` → `@Auditable(operation = "UPDATE")`
- `BankDetailsServiceImpl.create/update()` — аналогично
- `BranchServiceImpl.create/update()` — аналогично
- `CertificateServiceImpl.create/update()` — аналогично
- `LicenseServiceImpl.create/update()` — аналогично

### Проблема 2 (🟡 P1): public-info аудит не публиковал в Kafka
`AuditServiceImpl` (public-info) только писал в БД, без отправки в `audit.logs`.
Изменения банкоматов/отделений не попадали в `history-service`.

**Исправление:**
- Создан `producer/AuditProducer.java` — SmallRye Emitter → канал `audit-out` → топик `audit.logs`
- В `AuditServiceImpl`: после `repository.persist(audit)` добавлен `auditProducer.sendAudit()`
- В `application.properties`: добавлен канал `mp.messaging.outgoing.audit-out` (+ тест-профиль)

### Проблема 3 (🟡 P1): Транзакция аудита была вложена в бизнес-транзакцию
`@Transactional` (без типа) — откат бизнес-метода откатывал аудит.

**Исправление:** `@Transactional(Transactional.TxType.REQUIRES_NEW)` в `createAudit()` и `updateAudit()`.

### Проблема 4 (🟡 P1): profile-service — хрупкий `EntityType.valueOf()`
Вызов без try-catch: при появлении нового DTO → `IllegalArgumentException` в runtime.

**Исправление:** оба вызова в `AuditServiceImpl` (profile) обёрнуты в try-catch с fallback на `None`.

---

## ✅ Выполнение промпта от коллеги-AI (Kafka-архитектура)

| # | Приоритет | Что | Где | Результат |
|---|-----------|-----|-----|-----------|
| P0-1 | 🔴 | `error.logging` → `error.logs` | transfer, authorization | ✅ Уже было верно в коде |
| P0-2 | 🔴 | `payment.status.changed` в report-service | report-service | ✅ Уже реализовано `handlePaymentStatusChanged()` |
| P0-3 | 🔴 | `card.*` события | notification-service | ✅ `CardNotificationConsumer` уже существует |
| P1-4 | 🟡 | Мёртвый топик `suspicious-transfers.result` | antifraud | ✅ Удалены 3 `outboxHelper.enqueue()` + импорт + поле в ServiceImpl; `AntifraudOutboxHelper.java` удалён; топик `suspicious-transfers.create` убран из `KafkaTopic.java`; ссылка убрана из `transfer/application-kubernetes.yaml` |
| P1-5 | 🟡 | Мёртвый `external.audit.logs` | account-service | ✅ Удалены: свойство из `application.yaml`, поле `externalAuditLogs` из `KafkaTopicsConfig`, bean `getExternalAuditLogs()` из `KafkaTopic`, deprecated поле из `AuditProducer` |
| P1-6 | 🟡 | Мёртвый `audit.history` / `sendAuditHistory()` | transfer | ✅ Не найден в коде — уже исправлено в предыдущих чатах |
| P2 | ℹ️ | Документировать entry-points | — | ✅ Задокументировано в KAFKA_TOPOLOGY.md |

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

### 🟡 P2 — antifraud Outbox инфраструктура (cleanup)
`OutboxRelayScheduler`, `OutboxRepository`, `OutboxEvent` в antifraud-service стали no-op
после удаления `AntifraudOutboxHelper`. Оставлены для совместимости со схемой БД.
Можно удалить вместе с Liquibase-миграцией DROP TABLE antifraud_outbox в следующем релизе.

### 🟡 P2 — JaCoCo финальный прогон
`mvn test` по всем Spring Boot сервисам:
```
cd spring-boot-services && mvn test -pl account,antifraud,authorization,transfer,payment-api
```

### 🟢 P3 — profile-service AuditService interface
Имена методов `create(Object)` / `update(Object)` непоследовательны по сравнению
с public-info (`createAudit(T)` / `updateAudit(T)`). Можно переименовать для единообразия.

### 🟢 P3 — public-info AuditInterceptor: coverage тестами
`AuditInterceptor` теперь активен, но покрытия тестами нет.
Добавить unit-тест на перехват create/update через CDI-контекст.

---

## Как начать следующий чат

Приложи архив `xyzbank_v12_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V12.md внутри архива и продолжай работу.
Следующая задача: JaCoCo финальный прогон + cleanup antifraud Outbox инфраструктуры.
```
