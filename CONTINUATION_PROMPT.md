# XYZ-Bank — продолжение работы над проектом

## Контекст проекта

Учебная микросервисная банковская платформа **XYZ-Bank** на Java. ZIP с актуальным состоянием кода прилагается.

**Архитектура:**
- **Spring Boot 3.2.2** (сервисы с высокой бизнес-критичностью): `account :8085`, `antifraud :8086`, `authorization :8087`, `transfer :8092`, `payment-api :8079`
- **Quarkus 3.8.2** (event-driven, простой CRUD): `notification-service :8082`, `report-service :8083`, `history-service :8084`, `profile-service :8094`
- **Инфраструктура:** PostgreSQL :5433 (единая БД, 9 схем), Kafka :9092, Redis :6379, MailHog :1025/:8025

## Что уже сделано в предыдущем чате

### ✅ history-service-quarkus (полная доработка)
- Добавлены пустые пакеты: `exception/GlobalExceptionHandler` (JAX-RS ExceptionMapper @Provider), `mapper/HistoryMapper` (MapStruct, componentModel=JAKARTA), `service/HistoryService` + `HistoryServiceImpl`
- Добавлены новые DTO: `PagedResponse<T>` (аналог Spring Page<T>), `ErrorResponse`
- Все эндпоинты переведены на постраничный вывод (`?page=0&size=20`)
- `HistoryResource` — убрано прямое обращение к репозиторию, теперь через сервис
- `HistoryKafkaListener` — убрана инъекция репозитория, добавлен @Blocking, добавлены реальные DLQ-обработчики (не закомментированные)
- `application.properties` — добавлена конфигурация DLQ (`failure-strategy=dead-letter-queue`)
- `pom.xml` — добавлен MapStruct + mapstruct-processor

### ✅ profile-service-quarkus (полная доработка)
- Добавлены DTO: `AccountDetailsDto`, `AuditDto`, `ErrorDto`, `RegistrationDto`
- Добавлены Entity: `AccountDetails`, `Audit`; в `Profile` добавлены поля `snils` (СНИЛС) и `inn` (ИНН)
- В `Registration` восстановлены поля `district`, `locality`, `houseBlock`
- Добавлены репозитории: `AccountDetailsRepository`, `ActualRegistrationRepository`, `AuditRepository`, `PassportRepository`, `RegistrationRepository`; в `ProfileRepository` добавлены `findBySnils`, `findByInn`
- Добавлены MapStruct маппёры: `AccountDetailsMapper`, `AuditMapper`, `PassportMapper`, `ProfileMapper`, `RegistrationMapper` (все componentModel=JAKARTA)
- Добавлен `exception/EntityNotUniqueException`
- Добавлен `handler/GlobalExceptionHandler` (JAX-RS @Provider)
- Добавлена Kafka: `consumer/ProfileConsumer`, `consumer/AccountDetailsConsumer`; `producer/ProfileProducer`, `producer/AccountDetailsProducer`, `producer/AuditProducer`, `producer/ErrorProducer`
- Добавлены сервисные интерфейсы: `BasicCrudService<T>`, `ProfileService`, `AccountDetailsService`, `ActualRegistrationService`, `AuditService`, `PassportService`, `RegistrationService`
- Добавлены реализации в `service/impl/`: `ProfileServiceImpl` (с проверкой уникальности СНИЛС/ИНН), `AccountDetailsServiceImpl`, `ActualRegistrationServiceImpl`, `AuditServiceImpl`, `PassportServiceImpl`, `RegistrationServiceImpl`
- Добавлены `util/KafkaTopic` (@ConfigProperty вместо @Value), `util/audit/EntityType`, `util/audit/OperationType`
- Добавлены Liquibase миграции: `db/changelog/db.changelog-master.yaml` + `release-0.1.0.0/changelog-001.xml` (все таблицы + уникальные индексы на email/snils/inn)
- `pom.xml` — добавлен MapStruct + mapstruct-processor + quarkus-jackson
- `ProfileResource` — убран `@RolesAllowed` (JWT не подключён), добавлен метод `PUT /{id}`

## Что нужно сделать (задачи по приоритету)

### 🔴 Приоритет 1 — Критические исправления

**1.1. payment-api — Race condition при идемпотентности**

В `PaymentService.createPayment()` нет обработки `DataIntegrityViolationException`. При 100k RPS два одновременных запроса могут оба пройти SELECT (не найти запись) и оба попытаться сделать INSERT. Один получит исключение от уникального индекса `uk_client_idempotency (client_id, idempotency_key)`, но оно не поймается.

Нужно добавить:
```java
} catch (DataIntegrityViolationException e) {
    // Race condition — параллельный запрос успел раньше
    return paymentRepository
        .findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
        .map(this::mapToResponse)
        .orElseThrow(() -> new PaymentProcessingException("Unexpected idempotency state"));
}
```

**1.2. authorization — JWT-фильтр не подключён к Spring Security**

В `SecurityConfig.java` используется `httpBasic(withDefaults())` вместо JWT-фильтра. `JwtTokenUtil` и `JwtValidator` реализованы, но не подключены к цепочке безопасности.

Нужно:
- Создать `JwtAuthenticationFilter extends OncePerRequestFilter`
- Добавить `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` в SecurityConfig
- Убрать или оставить httpBasic только для swagger-ui

**1.3. authorization — Слабый JWT-секрет**

В `JwtTokenUtil.java`:
```java
return Keys.hmacShaKeyFor(jwtSecret.getBytes()); // ← слабо
```
Нужен Base64-декодированный ключ ≥ 256 бит:
```java
return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
```
И в `application.yaml` секрет должен быть Base64-encoded строкой ≥ 32 байта.

**1.4. authorization — validateToken глотает все исключения**

```java
} catch (Exception e) { return false; } // ← плохо
```
Нужно разделить: `ExpiredJwtException` → вернуть специфичную ошибку 401 с телом `{"error": "token_expired"}`, `SignatureException` → 401 `{"error": "invalid_signature"}`, остальное → логировать и 401.

### 🟡 Приоритет 2 — Производительность

**2.1. antifraud — Заменить Spring Data JPA на Spring Data JDBC**

Сервис содержит только простой CRUD без сложных доменных методов в Entity, без каскадов. При 100k RPS аналитические запросы через JPA создают лишний overhead Hibernate Session + L1-кэш.

Что сделать:
- Заменить `JpaRepository<T, Long>` на `CrudRepository<T, Long>` из `spring-data-jdbc`
- Убрать `@Entity`, `@Table(schema=...)` из javax.persistence → использовать `@Table` из `org.springframework.data.relational.core.mapping`
- Убрать `@GeneratedValue` — Spring Data JDBC использует `@Id` + auto-increment
- Заменить `@Query` JPQL → нативный SQL через `@Query(nativeQuery = true)` или `NamedParameterJdbcTemplate`
- В `pom.xml`: убрать `spring-boot-starter-data-jpa` → добавить `spring-boot-starter-data-jdbc`

**2.2. transfer — Двойная обработка исключений**

В `TransferServiceImpl.java`:
```java
exceptionHandler.handleException(e, null); // обрабатывается
throw new RuntimeException("Failed to save ...", e); // и снова бросается
```
Нужно выбрать одно: либо обработать и вернуть результат, либо бросить и поймать выше.

**2.3. Настройка connection pool для PostgreSQL**

Сейчас 9 сервисов → одна БД без явных настроек пула. При 100k RPS это проблема.

В `docker-compose.yml` PostgreSQL нужно добавить:
```yaml
command: >
  postgres
  -c max_connections=500
  -c shared_buffers=256MB
  -c effective_cache_size=768MB
```

В каждом Spring-сервисе в `application.yaml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

В Quarkus `application.properties`:
```properties
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
```

### 🟢 Приоритет 3 — Качество кода

**3.1. profile-service — Добавить AuditInterceptor (аналог Spring AOP)**

В Spring-сервисах есть `AuditAspect` (@Around). В Quarkus нужен CDI Interceptor:
- Создать `@InterceptorBinding` аннотацию `@Auditable`
- Создать `@Interceptor @Auditable @Priority(APPLICATION)` класс `AuditInterceptor`
- Пометить методы create/update в ProfileServiceImpl, AccountDetailsServiceImpl

**3.2. profile-service — Добавить тесты**

Quarkus-сервисы (history, profile) полностью без тестов. Нужны минимум:
- `@QuarkusTest` для `ProfileResource` (REST endpoint тесты через RestAssured)
- `@QuarkusTest` для `HistoryResource`
- Мок Kafka через `@InjectMock`

**3.3. transfer — Перенести тесты в правильный пакет**

Тесты лежат в корне `src/test/java/` (дефолтный пакет), нужно перенести в `com.bank.transfer.*`.

**3.4. Добавить Transactional Outbox для payment-api**

Текущая схема: платёж сохраняется в БД → затем отправляется в Kafka. Если Kafka упала — уведомление потеряется (проблема из вопроса 4 консультанта).

Нужно:
1. Создать таблицу `outbox_events (id, event_type, payload, created_at, sent_at, status)`
2. В `createPayment()` в той же транзакции сохранять и платёж, и событие в outbox
3. Создать `@Scheduled` джобу `OutboxProcessor`, которая каждые N секунд читает неотправленные события и публикует их в Kafka
4. После успешной отправки помечать событие как `SENT`

**3.5. Добавить Read Replica для report-service**

При 100k RPS отчётность не должна читать из мастер-БД. В `docker-compose.yml` добавить PostgreSQL replica, в `ReportService` для чтения использовать отдельный DataSource с `@Transactional(readOnly = true)`.

## Напоминание об архитектурных принципах

- **Quarkus-сервисы:** `@ApplicationScoped` вместо `@Service`, `@Inject` вместо `@Autowired`, `@ConfigProperty` вместо `@Value`, `@Incoming`/`@Outgoing` вместо `@KafkaListener`/`KafkaTemplate`, `ExceptionMapper @Provider` вместо `@RestControllerAdvice`, MapStruct `componentModel = MappingConstants.ComponentModel.JAKARTA`
- **DLQ в Quarkus:** через `failure-strategy=dead-letter-queue` в `application.properties`, отдельный `@Incoming` на DLQ-топик
- **AOP в Quarkus:** CDI Interceptors (`@InterceptorBinding` + `@Interceptor` + `@AroundInvoke`) вместо Spring `@Aspect` + `@Around`
- Пустые пакеты (exception, mapper, service, kafka) в Quarkus-сервисах — это незаконченная работа, не особенность фреймворка

## Важно

Архив содержит **весь проект целиком**. При изменении конкретного микросервиса нужно обновить файлы внутри него и переупаковать весь проект в zip — не выдавать один микросервис как отдельный проект.
