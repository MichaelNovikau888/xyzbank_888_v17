# XYZ-Bank — Продолжение работы. Промпт для нового чата.

## Контекст проекта

Учебная микросервисная банковская платформа **XYZ-Bank** на Java.
ZIP с актуальным состоянием кода прилагается.

**Архитектура:**
- **Spring Boot 3.2.2** (5 сервисов):
  - `account :8085`, `antifraud :8086`, `authorization :8087`, `transfer :8092`, `payment-api :8079`
- **Quarkus** (5 сервисов):
  - `notification-service :8082` (3.30)
  - `report-service :8083` (3.30)
  - `history-service-quarkus :8084` (3.8)
  - `profile-service-quarkus :8094` (3.8)
  - `public-info-quarkus :8095` (3.8)
- **Инфраструктура:** PostgreSQL :5433 (единая БД, 10 схем), Kafka :9092,
  Redis :6379, MailHog :1025/:8025

---

## Что сделано за последние чаты (не трогать)

### payment-api
- `IdempotencyKeyGenerator` — сервер генерирует ключ (не клиент!),
  алгоритм SipHash-24 (Guava), секретный ключ k0/k1 из application.yml,
  3-секундное временное окно (`System.currentTimeMillis() / 3000`),
  разделитель `\u0000` против атак склейки
- `PaymentController` — убран `@RequestHeader("Idempotency-Key")`,
  ключ генерируется через `keyGenerator.generateKey(...)` на сервере
- `PaymentIntegrationTest` — все тесты переведены на 2-арг `postPayment(clientId, request)`
- Guava `33.2.1-jre` добавлена в pom.xml
- application.yml / application-test.yml — секция `payment.idempotency.sip-hash-k0/k1`

### transfer-service — двойная обработка исключений (RED FLAG ЗАКРЫТ)
- `GlobalExceptionHandler` → переименован в `KafkaErrorPublisher`
  (это не HTTP-handler, а Kafka publisher ошибок)
- Метод `handleException` → `publish`
- `TransferServiceImpl` — убрана двойная обработка:
  было `log + handler` без throw (транзакция НЕ откатывалась!),
  стало `log → publisher.publish() → throw` (@Transactional теперь откатывает)
- `AuditServiceImpl` — убрана тройная обработка в `getAuditHistory()`
- `AuditConsumer`, `TransferConsumer` — обновлены на `KafkaErrorPublisher`
- Все тесты обновлены: `exceptionHandler` → `errorPublisher`,
  `verify(exceptionHandler).handleException` → `verify(errorPublisher).publish`

### report-service
- `ReportServiceTest.setUp()` — исправлен red flag "Unhandled exception: java.lang.Exception"
  на строке 68: `callable.call()` обёрнут в try-catch с `throw new RuntimeException(e)`
- Убран `@SuppressWarnings({"unchecked", "CallToSignatureWithUncheckedExceptionThrows"}`
  с `setUp()` — больше не нужен

### frontend (index.html)
- Полностью переписан JS: убраны mockUsers, mockTx, фейковый state
- Добавлены: `api()`, `apiRetry()`, `debounce()`, `normalizeTx()`,
  `loadDashboardData()`, `logout()`, глобальный спиннер, кнопка Выйти
- Мобильная адаптация: bottom-nav таббар (≤768px), safe-area-inset-bottom,
  font-size:16px на input (нет zoom на iOS), -webkit-tap-highlight-color
- Эндпоинты: auth:8087, account:8085, transfer:8092, payment:8079

### Исправления из предыдущих чатов (уже сделано, не трогать)
- `accountNumber: Long → String` в transfer-service + Liquibase миграция release-0.3.0.0
- `AuditProducer` antifraud: `"audit-topic"` → `"audit.logs"`
- `KafkaErrorPublisher` (бывший GlobalExceptionHandler) во всём transfer
- docker-compose.yml, prometheus.yml, alerts/*.yml — кракозябры убраны,
  все комментарии на чистом ASCII/English
- `TestConstants.ACCOUNT_NUMBER` = `"12345678901234567890"` (String, 20 цифр)

---

## Что ещё НЕ сделано / требует внимания

### 🔴 notification-service (Quarkus :8082) — СЛЕДУЮЩАЯ ЗАДАЧА
Статус: базовая структура есть, но сервис недоделан.
Что нужно проверить и доделать:
- `NotificationConsumer` слушает `payment.created` и `payment.status.changed`
- `EmailService` — реально ли отправляет через MailHog?
- Тесты: `NotificationConsumerTest` — покрытие?
- Нет интеграционного теста с реальным Kafka + MailHog
- Проверить application.properties: все ли настройки корректны?

### 🔴 report-service (Quarkus :8083) — СЛЕДУЮЩАЯ ЗАДАЧА
Статус: базовая структура есть, тест ReportServiceTest исправлен.
Что нужно проверить и доделать:
- `ReportService.generateDailyReportCsv()` — работает ли пагинация?
- `ReportResource` — REST endpoint для скачивания CSV
- Read Replica datasource (`@DataSource("replica")`) — корректно настроена?
- `checkPartitionHealth()` — что именно проверяет?
- Нет интеграционного теста с реальной PostgreSQL (Testcontainers)

### CORS — критично для фронта
Фронт переписан на реальный API, но без CORS на бэкенде не заработает.
Нужно добавить `@CrossOrigin` или `CorsConfigurationSource` в:
- authorization :8087
- account :8085
- transfer :8092
- payment-api :8079

### AuditServiceImplTest (transfer)
`updateAudit_Success()` вызывает `updateAudit(1, auditDto)` (int литерал).
При сигнатуре `long id` — работает (widening), но стоит заменить на `1L`.

---

## Архитектурные принципы (важно при работе с кодом)

| Spring Boot | Quarkus |
|---|---|
| @Service | @ApplicationScoped |
| @Autowired | @Inject |
| @Value | @ConfigProperty |
| @KafkaListener | @Incoming("channel-name") + @Blocking |
| KafkaTemplate.send() | Emitter<T> + @Channel("name") |
| @RestControllerAdvice | @Provider ExceptionMapper<E> (top-level!) |
| @Mapper(componentModel="spring") | componentModel = MappingConstants.ComponentModel.JAKARTA |
| JpaRepository<T,Long> | PanacheRepository<T> |
| Spring Page<T> | PagedResponse<T> (самописный DTO) |
| application.yaml | application.properties |

**@InjectMock**: import io.quarkus.test.InjectMock (не deprecated с Quarkus 3.2).

**Kafka channel names**: только буквы, цифры и дефисы. Точки запрещены.
Топик Kafka задаётся через `.topic=` в properties отдельно.

**PanacheRepository**: использовать `findByIdOptional()` вместо `findById()`
когда нужен Optional<T>.

**Тест-зависимости**: assertj-core:3.25.3 нужна явная версия.

**Enum с полями**: @Getter + @RequiredArgsConstructor от Lombok.

**Комментарии в Java**: `/**` для Javadoc, `/*` для блоков. Строки `" * текст"` НЕ менять.

**KafkaErrorPublisher** (transfer): это НЕ Spring MVC handler, а Kafka publisher ошибок.
Метод называется `publish(Exception e, String requestId)`.

**SipHash-24** (payment-api): ключи k0/k1 из `${payment.idempotency.sip-hash-k0}`.
В тестах используются тестовые ключи из application-test.yml.
Разделитель `\u0000` между полями (защита от атак склейки).
