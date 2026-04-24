# CONTINUATION_PROMPT_V6 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v6)

### Чаты 1–5 (см. CONTINUATION_PROMPT_V5.md)
<сохранена вся история — history-service, antifraud, transfer-outbox, notification-service,
Long→String рефакторинг, тесты, kafka-аудит. Подробности в V5.md>

### Чат 6 — мёртвые топики, валидация, docker-compose (основная работа)

#### Задача 1: antifraud/KafkaTopic.java
Удалены 4 мёртвых `@Bean`: `updateTopic`, `deleteTopic`, `getTopic`, `responseTopic`
(suspicious-transfers.update/delete/get/response — никто не подписан).
Оставлены: `createTopic`, `logsTopic`, `paymentAntifraud*`, `transferAntifraud*`.

#### Задача 1б: SuspiciousTransferProducer.java
Удалены методы `createEvent/updateEvent/deleteEvent/getEvent`.
Остались только `sendAntifraudResponse()` и `sendTransferAntifraudResponse()`.
`SuspiciousTransferProducerTest.java` — полностью переписан (4 теста).
`TopicTestUtil.java` — удалён.

#### Задача 2: Удалены целиком
- `TransferConsumer.java` + `TransferConsumerTest.java`
- `AuditConsumer.java` + `AuditConsumerTest.java`
- `TransferProducer.java` + `TransferProducerTest.java`
- `KafkaConfigConsumer` — вычищены `auditConsumerFactory`, `transferConsumerFactory`,
  `kafkaListenerContainerFactory`, `auditKafkaListenerContainerFactory`.
  Остался только `transferAntifraudResponseListenerContainerFactory`.
- `TransferServiceImpl` — убраны `saveToOutbox("suspicious-transfers.create")` (3 вызова),
  мёртвый метод `saveToOutbox()`, поля `OutboxRepository`/`ObjectMapper`.

**Причина:** дублирующий аудит — `AuditConsumer` слушал `transfer.account/card/phone`
и повторно писал историю. Теперь единственный путь:
`TransferServiceImpl → AuditServiceImpl → HistoryOutboxHelper → transfer.events → history-service`.

#### Задача 3: @Pattern валидация в DTO
- `AccountTransferDto` — `@Pattern(regexp = "\\d{20}")`, `@NotBlank`, `@NotNull`, `@Positive`
- `CardTransferDto` — `@Pattern(regexp = "\\d{13,19}")`
- `PhoneTransferDto` — `@Pattern(regexp = "\\+?[0-9]{10,15}")`
- `AccountDto` (account-service) — `@Pattern(regexp = "\\d{20}")`

#### Задача 4: docker-compose.yml
Добавлен сервис `kafka-setup` — создаёт 21 топик через `kafka-topics --create --if-not-exists`.

#### Задача 5: notification-service — ProfileServiceClient + ClientService
- `ProfileServiceClient.java` (новый) — MicroProfile REST-клиент к profile-service,
  `GET /api/profiles/{id}`, `@Timeout(2000)`, `@RegisterRestClient(configKey = "profile-api")`
- `ClientService.java` — полная реализация: Redis кэш TTL=1ч → REST к profile-service → null fallback
- `application.properties` — добавлен `quarkus.rest-client.profile-api.*`
- `PushNotificationService.fetchPushTokenFromProfile()` — оставлена заглушка (null),
  задокументировано: push-токены хранятся в Redis, в profile-service их нет.

#### Задача 6: Двойной аудит — решена удалением AuditConsumer (см. Задача 2 выше)

#### Задача 7: CardNotificationConsumerTest.java (новый, 21 тест)
Покрыты: CREATED/BLOCKED/UNBLOCKED/LIMIT_CHANGED, Redis dedup, fail-open,
невалидный JSON, null eventType/cardId, clientId=null fallback, ошибка push → rethrow.

#### Задача 8 (Qodana P0/P1/P2): частично выполнена
- **CDI @ApplicationScoped** — все 19 файлов из Qodana-чеклиста уже имеют аннотацию ✅
- **spring.cloud.kubernetes.*** — **ОСТАВЛЕНО** (задел на будущее, Qodana ругается — допустимо)
- **PaymentController — Unknown HTTP Headers** ✅
  - Добавлены `@Parameter(description, required=true)` к `@RequestHeader("Client-Id")`
  - Добавлены `@Operation` на каждый метод, `@Tag` на контроллер
  - Добавлена зависимость `springdoc-openapi-starter-webmvc-ui` в `payment-api/pom.xml`
- **TransferServiceImpl — Condition always true** ✅
  - `maskCardNumber()`: убран `cardNumber != null ? cardNumber : ""` → просто `cardNumber`
  - `maskPhoneNumber()`: аналогично

---

## Актуальная Kafka-карта (после всех исправлений)

```
payment-api → payment.created              → notification (push+email), report (CSV)
payment-api → payment.status.changed       → notification, report ✅
payment-api → payment.antifraud.check      → antifraud
antifraud   → payment.antifraud.response   → payment-api
payment-api → account.events               → history-service

transfer → transfer.antifraud.check        → antifraud
antifraud → transfer.antifraud.response    → transfer
transfer → transfer.events                 → history-service (через HistoryOutboxHelper)
transfer → transfer.notification           → notification (через TransferNotificationOutboxHelper)

account → card.created/blocked/unblocked/limit.changed → notification ✅

account ↔ authorization: account.create/update/delete/get/getById + external.*
account ↔ authorization: auth.validate / auth.validate.response

audit.logs  → history-service  (producers: antifraud, account, profile)
error.logs  → history-service  (producers: transfer, authorization, profile)
```

**Outbox destinations (transfer-service):**
| Helper | Топик | Получатель |
|---|---|---|
| `HistoryOutboxHelper` | `transfer.events` | history-service |
| `AntifraudOutboxHelper` | `transfer.antifraud.check` | antifraud |
| `TransferNotificationOutboxHelper` | `transfer.notification` | notification-service |

---

## Что нужно сделать в следующем чате

### 1. 🟢 P2 — JaCoCo: code coverage для Spring Boot сервисов
**`spring-boot-services/pom.xml`** — добавить в `<build><plugins>`:
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
  </executions>
</plugin>
```
Цель: `mvn test` → генерирует `target/site/jacoco/index.html`.

### 2. 🔴 P0 — Qodana: оставшиеся CDI-предупреждения (расследовать)
Qodana флагует "Unsatisfied dependency" не только для *Impl классов,
но и для `@Inject` полей где тип — интерфейс без зарегистрированного бина.
Запустить локально и проверить актуальный список:
```bash
docker run --rm -it \
  -v $(pwd):/data/project \
  jetbrains/qodana-jvm \
  --save-report
```
Ожидаемый результат после всех исправлений: ≤50 issues (с 233).

### 3. 🟡 P1 — TransferNotificationConsumer: поддержка REVIEW статуса
`AntifraudResponseConsumer` возвращает `REVIEW` (сумма 10k-50k).
Сейчас `TransferNotificationConsumer` не имеет кейса для `REVIEW`.
Добавить push-уведомление: "Перевод на проверке — ожидайте до 24 часов".

### 4. 🟡 P1 — ClientService: интеграционный тест
Написать `ClientServiceTest` (Quarkus):
```
- email найден в Redis → REST не вызывается
- email не в Redis → REST вызов → результат кэшируется
- REST недоступен → null, нет исключения
- clientId не числовой → null без REST-вызова
```

### 5. 🟡 P1 — AuditServiceImpl (transfer): упростить
`AuditServiceImpl` всё ещё принимает DTO через `AuditConsumer`-паттерн (legacy interface).
Проверить что `AuditService` интерфейс + `AuditServiceImpl` всё ещё вызываются из
`TransferServiceImpl` напрямую и нигде не через Kafka.

### 6. ℹ️ P2 — Qodana: spring.cloud.kubernetes.*
**Намеренно оставлено** — задел на будущее Kubernetes-деплой.
Qodana будет флагать 6 warnings — это допустимо.
Когда придёт время: добавить `spring-cloud-starter-kubernetes` в `pom.xml`.

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн (OutboxRelayScheduler) во всех Spring Boot сервисах
- **PostgreSQL**: каждый сервис своя схема; Liquibase для Spring Boot, Hibernate для Quarkus
- **Redis**: notification-service (dedup TTL=24h + email cache TTL=1h + push token TTL=30d)
- **FCM**: Firebase Cloud Messaging для push-уведомлений
- **Swagger**: springdoc-openapi 2.3.0

---

## Как начать следующий чат

Приложи архив `xyzbank_v6_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V6.md внутри архива и продолжай работу.
Следующая задача: добавить JaCoCo в spring-boot-services/pom.xml,
проверить AuditServiceImpl (transfer) на легаси-зависимости,
добавить REVIEW статус в TransferNotificationConsumer.
```
