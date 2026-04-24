# CONTINUATION_PROMPT_V17 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank.
Архив: `xyzbank_v17_complete.zip` (рабочая директория: `xyzbank_work_v8/`).

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus 3.30.2**: history-service, notification-service, report-service, profile-service, public-info-quarkus
- **Kafka**: Outbox-паттерн (Spring Boot) / SmallRye Reactive Messaging (Quarkus)
- **PostgreSQL + Liquibase**, Redis, FCM, jjwt 0.11.5, JaCoCo 0.8.12

---

## Что сделано в чате 17 — единая схема ErrorEvent для топика error.logs

### Проблема
Все сервисы писали в топик `error.logs` разные форматы:
- profile-service: `{"timestamp":"...","message":"..."}`
- public-info: `{"errorCode":"...","message":"...","timestamp":"..."}`
- authorization: `{"requestId":"...","success":false,"message":"..."}`
- transfer: `{"occurredAt":"...","status":404,"error":"...","message":"...","requestId":"..."}`
- account: `{"errorCode":"...","message":"...","timestamp":"..."}`

history-service сохранял всё как raw String — структурный анализ был невозможен.

### Решение — единый `ErrorEvent`

```json
{
  "serviceName": "transfer-service",
  "errorCode":   "NOT_FOUND",
  "message":     "Transfer not found: 42",
  "httpStatus":  404,
  "requestId":   "uuid-...",
  "stackTrace":  null,
  "occurredAt":  "2026-04-23T10:15:30"
}
```

Поля `httpStatus`, `requestId`, `stackTrace` — опциональны (`@JsonInclude(NON_NULL)`).

### Стандартные коды (`errorCode`)
| Код | Значение |
|-----|----------|
| `NOT_FOUND` | Сущность не найдена (404) |
| `VALIDATION_ERROR` | Ошибка валидации (400) |
| `CONFLICT` | Конфликт / дубликат (409) |
| `UNAUTHORIZED` | Не аутентифицирован (401) |
| `FORBIDDEN` | Нет прав (403) |
| `INTERNAL_ERROR` | Внутренняя ошибка (500) |
| `KAFKA_ERROR` | Ошибка Kafka-обработчика |

### Изменения по сервисам

| Сервис | Изменён файл | Суть |
|--------|-------------|------|
| profile-service | `dto/ErrorEvent.java` (NEW) | Единая схема |
| profile-service | `kafka/producer/ErrorProducer.java` | Шлёт `ErrorEvent`, фабричные методы `sendNotFound/sendConflict/sendInternalError` |
| profile-service | `handler/GlobalExceptionHandler.java` | Использует фабричные методы ErrorProducer |
| public-info | `dto/ErrorEvent.java` (NEW) | Единая схема |
| public-info | `producer/ErrorProducer.java` | Шлёт `ErrorEvent` |
| public-info | `consumer/ErrorLogsConsumer.java` | Десериализует `ErrorEvent`, структурное логирование |
| account | `exception/error_dto/ErrorEvent.java` (NEW) | Единая схема |
| account | `exception/KafkaErrorSender.java` | Шлёт `ErrorEvent`, добавлена перегрузка с requestId |
| account | `exception/GlobalExceptionHandler.java` | Добавлен метод `toErrorCode(Exception)` |
| authorization | `dto/ErrorEvent.java` (NEW) | Единая схема |
| authorization | `handler/KafkaExceptionHandler.java` | Шлёт `ErrorEvent` вместо `KafkaResponse` |
| transfer | `exception/ErrorEvent.java` (NEW) | Единая схема |
| transfer | `exception/KafkaErrorPublisher.java` | Шлёт `ErrorEvent`, добавлена перегрузка без requestId |
| history-service | `dto/ErrorEvent.java` (NEW) | Единая схема для десериализации |
| history-service | `kafka/HistoryKafkaListener.java` | `handleErrorLog` десериализует `ErrorEvent`, использует `serviceName` и `errorCode` из payload |

### Новые тесты
- `profile-service/dto/ErrorEventTest.java` — 11 тестов: Builder, коды, JSON round-trip,
  cross-service десериализация (имитация JSON от authorization-service)

---

## Итоговое покрытие тестами (~847 тестов)

| Сервис | Тестов |
|--------|--------|
| antifraud | 69 |
| account | 94 |
| authorization | 48 |
| transfer | 39 |
| payment-api | 24 |
| history-service | 94 |
| notification-service | 140 |
| profile-service | 71 (+11 ErrorEventTest) |
| public-info | 131 |
| report-service | 81 |
| **ИТОГО** | **~847** |

---

## Открытые задачи

### 🟡 P2 — JaCoCo финальный прогон Spring Boot сервисов
```bash
cd spring-boot-services
mvn test -pl account,antifraud,authorization,transfer,payment-api
```

### 🟡 P2 — authorization: KafkaTemplate тип изменился
`KafkaTemplate<String, KafkaResponse>` → `KafkaTemplate<String, ErrorEvent>`.
Нужно обновить Spring-конфигурацию Kafka Producer (ProducerFactory) в authorization.

### 🟡 P2 — account: KafkaTemplate тип изменился
`KafkaTemplate<String, ErrorResponse>` → `KafkaTemplate<String, ErrorEvent>`.
Обновить ProducerFactory/KafkaConfig в account.

### 🟢 P3 — profile-service: AuditService именование
`create(Object)` / `update(Object)` → `createAudit(T)` / `updateAudit(T)`.

### 🟢 P3 — antifraud: финальный DROP outbox
```xml
<!-- release-0.4.0.0/drop-outbox-events.xml -->
<dropTable tableName="outbox_events"/>
```

### 🟢 P3 — Swagger/OpenAPI: history-service, notification-service, profile-service

---

## Как начать следующий чат

```
Прочитай CONTINUATION_PROMPT_V17.md внутри архива и продолжай работу.
Следующая задача: обновить KafkaTemplate конфигурацию в account и authorization
под новый тип ErrorEvent (P2).
```
