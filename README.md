# XYZ-Bank — Unified Microservices Platform

Гибридная архитектура: **Spring Boot 3** + **Quarkus 3** в одном Maven проекте.

---

## 🏛️ Архитектура: почему Spring Boot И Quarkus?

| Сервис | Фреймворк | Причина |
|---|---|---|
| account | Spring Boot | Сложная JPA-логика, кредитные карты, Kafka producers |
| antifraud | Spring Boot | Stateful ML-правила, сложная бизнес-логика |
| authorization | Spring Boot | Spring Security, JWT-инфраструктура |
| transfer | Spring Boot | Сложные транзакции, SAGA-паттерн |
| payment-api | Spring Boot | REST → Kafka gateway, идемпотентность |
| **notification-service** | **Quarkus** | Событийная нагрузка: Kafka → email/Redis. Scale-to-zero |
| **report-service** | **Quarkus** | Batch: запустился → посчитал → завершился. Нет JVM-прогрева |
| **history-service** | **Quarkus** | Простой CRUD аудита. PanacheEntityBase сокращает код |
| **profile-service** | **Quarkus** | Простой CRUD профилей. Быстрый старт Dev Experience |
| **public-info-quarkus** | **Quarkus** | Публичная информация банка (отделения, банкоматы). Без авторизации |

---

## 🏗️ Структура Maven-проекта

```
xyzbank/
├── pom.xml                          ← АГРЕГАТОР (без parent!)
│
├── spring-boot-services/            ← Sub-агрегатор Spring Boot
│   ├── pom.xml                      ← parent: spring-boot-starter-parent:3.2.2
│   ├── account/       :8085
│   ├── antifraud/     :8086
│   ├── authorization/ :8087
│   ├── transfer/      :8092
│   └── payment-api/   :8079
│
├── notification-service/  :8082     ← Quarkus 3.30 (Kafka → email)
├── report-service/        :8083     ← Quarkus 3.30 (Kafka → PostgreSQL)
├── history-service-quarkus/ :8084   ← Quarkus 3.8 (аудит событий)
├── profile-service-quarkus/ :8094   ← Quarkus 3.8 (CRUD профилей)
├── public-info-quarkus/   :8095     ← Quarkus 3.8 (публичная информация банка)
│
├── docker-compose.yml
├── init-schemas.sql
└── frontend/index.html
```

### ⚙️ Почему агрегатор без parent?

Spring Boot и Quarkus **нельзя** объединить через общий `<parent>` — у них конфликтуют
версии зависимостей (Jakarta EE, Jackson, Netty). Решение: корневой `pom.xml` имеет
`<packaging>pom</packaging>` без `<parent>`. Каждая группа объявляет своего родителя сама.

---

## 🚀 Быстрый старт

### 1. Инфраструктура

```bash
docker-compose up -d
# PostgreSQL :5433, Kafka :9092, Redis :6379, MailHog :1025/:8025, Kafdrop :9000
```

### 2. Spring Boot сервисы (IntelliJ IDEA)

Открой `spring-boot-services/` как Maven-проект, запускай с профилем `local`:
```
-Dspring.profiles.active=local
```

| Сервис | Порт | Swagger |
|---|---|---|
| authorization | 8087 | http://localhost:8087/api/authorization/swagger-ui/index.html |
| account | 8085 | http://localhost:8085/api/account/swagger-ui/index.html |
| antifraud | 8086 | http://localhost:8086/api/antifraud/swagger-ui/index.html |
| transfer | 8092 | http://localhost:8092/api/transfer/swagger-ui/index.html |
| payment-api | 8079 | http://localhost:8079/swagger-ui/index.html |

### 3. Quarkus сервисы

```bash
# Dev mode с live-reload
cd notification-service && mvn quarkus:dev
cd report-service       && mvn quarkus:dev
cd history-service-quarkus  && mvn quarkus:dev
cd profile-service-quarkus  && mvn quarkus:dev
cd public-info-quarkus      && mvn quarkus:dev
```

| Сервис | Порт | Swagger UI |
|---|---|---|
| notification-service | 8082 | http://localhost:8082/q/swagger-ui |
| report-service | 8083 | http://localhost:8083/q/swagger-ui |
| history-service | 8084 | http://localhost:8084/q/swagger-ui |
| profile-service | 8094 | http://localhost:8094/q/swagger-ui |
| public-info-quarkus | 8095 | http://localhost:8095/q/swagger-ui |

### 4. Порядок запуска

```
1. docker-compose up -d
2. authorization  (JWT-провайдер, нужен остальным)
3. account + antifraud + transfer  (порядок не важен)
4. payment-api
5. notification-service + report-service + history-service + profile-service
```

---

## 🔑 Spring Boot ↔ Quarkus: ключевые различия в коде

| Аспект | Spring Boot | Quarkus |
|---|---|---|
| DI | `@Autowired` / `@Service` | `@Inject` / `@ApplicationScoped` |
| REST | `@RestController` + `@GetMapping` | `@Path` + `@GET` (JAX-RS) |
| ORM | `JpaRepository` | `PanacheEntityBase` / `PanacheRepository` |
| Kafka consumer | `@KafkaListener` | `@Incoming("channel")` (SmallRye) |
| Config | `application.yaml` | `application.properties` |
| Transactional | `@Transactional` (Spring) | `@Transactional` (Jakarta) |
| Swagger | springdoc-openapi | quarkus-smallrye-openapi |
| Logging | SLF4J/Logback | JBoss Logging |

---

## 🧪 Тестирование

```bash
# Создать платёж (payment-api → Kafka → notification + report)
curl -X POST http://localhost:8079/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -H "Client-Id: client-123" \
  -d '{"recipientAccount":"40817810099910004312","amount":5000,"currency":"RUB"}'

# Проверить письмо в MailHog
open http://localhost:8025

# Проверить отчёт за сегодня
curl http://localhost:8083/api/reports/daily/$(date +%Y-%m-%d)

# Проверить историю событий
curl http://localhost:8084/api/history

# Kafka-топики (Kafdrop)
open http://localhost:9000
```

---

## 🗄️ База данных

Единый PostgreSQL `:5433/postgres`, разделение по схемам:

| Схема | Сервис |
|---|---|
| `account` | account (Spring Boot) |
| `anti_fraud` | antifraud (Spring Boot) |
| `authorization` | authorization (Spring Boot) |
| `transfer` | transfer (Spring Boot) |
| `fastpay` | payment-api (Spring Boot) |
| `notification` | notification-service (Quarkus) |
| `report` | report-service (Quarkus) |
| `history` | history-service (Quarkus) |
| `profile` | profile-service (Quarkus) |


---

## 📦 Пакеты (package naming)

| Сервис | Было | Стало |
|---|---|---|
| payment-api | `ru.examplebank.fastpay.paymentapi` | `com.bank.payment` |
| report-service | `ru.examplebank.fastpay` | `com.bank.report` |
| notification-service | `ru.examplebank.fastpay` | `com.bank.notification` |
| history-service | `com.bank.history` | без изменений |
| profile-service | `com.bank.profile` | без изменений |
| Spring Boot сервисы | `com.bank.*` | без изменений |

---

## 🔐 Безопасность (authorization-service)

### JWT-инфраструктура

`JwtAuthenticationFilter` подключён к Spring Security цепочке (`addFilterBefore`). Сессии — `STATELESS`.

| Ошибка токена | HTTP-ответ |
|---|---|
| Истёкший токен | `401 {"error": "token_expired"}` |
| Неверная подпись | `401 {"error": "invalid_signature"}` |
| Прочие ошибки | `401 {"error": "invalid_token"}` |

**JWT-секрет** хранится как Base64-encoded строка ≥ 32 байта в `application-local.yaml`:
```yaml
app.jwt.secret-key: <base64-encoded-string-min-32-bytes>
```
Генерация нового секрета:
```bash
openssl rand -base64 32
```

---

## 📊 Мониторинг

### Grafana-дашборд: `xyz-bank-business.json`

Дашборд покрывает **все 9 сервисов**, включая 4 Quarkus:

| Секция | Панели |
|---|---|
| 💳 Payment API | Rate, Pending, Latency p50/p95/p99 |
| 🔄 Transfer | Rate, Amount dist., Duplicate rate |
| 🛡️ Antifraud | Analyzed/Blocked, Suspicious ratio |
| 🔐 Authorization | Login rate, Token validation, Brute-force |
| 🏦 Account | CRUD rate, Credit cards, Entity counts |
| 📦 Outbox Health | Relay rate, Pending events |
| **📊 Report Service** | **Payments stored, CSV rate, Latency p99** |
| **🔔 Notification Service** | **Email rate by event_type, Failed, Skipped** |
| **📜 History Service** | **Events saved/skipped, Kafka by source, DLQ** |
| **👤 Profile Service** | **Profile/AccountDetails CRUD, Duplicates** |

### Prometheus-алерты

Файлы в `monitoring/alerts/`:
- `business_alerts.yml` — бизнес-алерты (payment stopped, brute-force, email failures, DLQ events)
- `technical_alerts.yml` — технические алерты (outbox pile-up, Quarkus service down, duplicate rates)

---

## 🧪 Тестирование (автоматическое)

### Quarkus DevServices (Testcontainers)

Quarkus поднимает контейнеры **автоматически** при запуске тестов — никаких `docker-compose` вручную:

```bash
# report-service: PostgreSQL + Kafka
cd report-service && mvn test

# notification-service: PostgreSQL + Kafka + Redis
cd notification-service && mvn test

# history-service, profile-service
cd history-service-quarkus && mvn test
cd profile-service-quarkus && mvn test
```

### Spring Boot (Testcontainers вручную)

```bash
# Все Spring Boot сервисы
cd spring-boot-services && mvn test
```

### Покрытие тестами

| Сервис | Тип тестов |
|---|---|
| payment-api | `@SpringBootTest` + Testcontainers (PostgreSQL + Kafka) |
| authorization | Unit + Integration (PostgreSQL) |
| account / antifraud / transfer | Integration (PostgreSQL + Kafka) |
| report-service | `@QuarkusTest` REST + Unit (ReportService, DevServices) |
| notification-service | `@QuarkusTest` REST + Unit (NotificationConsumer с моками) |
| history-service | `@QuarkusTest` (DevServices) |
| profile-service | `@QuarkusTest` (DevServices) |

---

## 🏦 public-info-quarkus (порт :8095)

Переписанный на Quarkus сервис публичной информации о банке. Управляет пятью доменами: **BankDetails**, **Branch**, **ATM**, **Certificate**, **License** — плюс **Audit**-лог всех операций.

### Архитектурные решения при переводе Spring Boot → Quarkus

| Spring Boot (оригинал) | Quarkus (новый) | Причина |
|---|---|---|
| `@Service` + `@Autowired` | `@ApplicationScoped` + `@Inject` | CDI-стандарт |
| `JpaRepository<T, Long>` | `PanacheEntityBase` (публичные поля) | Panache — меньше шаблонного кода |
| `@KafkaListener(topics=...)` | `@Incoming("channel")` + `@Blocking` | SmallRye Reactive Messaging |
| `@Aspect @AfterReturning` | `@InterceptorBinding` + `@AroundInvoke` | CDI Interceptors |
| `@RestControllerAdvice` | `@Provider ExceptionMapper<RuntimeException>` | JAX-RS стандарт |
| `@Mapper(componentModel="spring")` | `componentModel = MappingConstants.ComponentModel.JAKARTA` | CDI-совместимость |
| `application.yaml` с `@Value` | `application.properties` + `@ConfigProperty` | Quarkus-стиль конфига |
| `Spring Page<T>` | `PagedResponse<T>` (собственный DTO) | Нет Spring зависимостей |

### REST API (порт 8095)

| Метод | Путь | Описание |
|---|---|---|
| GET/POST | `/api/public-info/bank-details` | Список / создание реквизитов банка |
| GET/PUT/DELETE | `/api/public-info/bank-details/{id}` | Получение / обновление / удаление |
| GET/POST | `/api/public-info/branches` | Отделения банка |
| GET/PUT/DELETE | `/api/public-info/branches/{id}` | CRUD отделения |
| GET/POST | `/api/public-info/atms?branchId=` | Банкоматы по отделению |
| GET/PUT/DELETE | `/api/public-info/atms/{id}` | CRUD банкомата |
| GET/POST | `/api/public-info/certificates?bankDetailsId=` | Сертификаты банка |
| GET/POST | `/api/public-info/licenses?bankDetailsId=` | Лицензии банка |
| GET | `/api/public-info/audits` | Audit-лог операций |
| GET | `/q/swagger-ui` | Swagger UI |
| GET | `/q/metrics` | Prometheus-метрики |
| GET | `/q/health` | Health check |

### Kafka-топики (все `@Incoming`, consumer-only)

```
public-info.bank.create / .update / .delete
public-info.branch.create / .update / .delete
public-info.atm.create / .update / .delete
public-info.certificate.create / .update / .delete
public-info.license.create / .update / .delete
```

### Метрики

`publicinfo_bank_details_created_total`, `publicinfo_branch_created_total`, `publicinfo_atm_created_total`, `publicinfo_certificate_created_total`, `publicinfo_license_created_total`, `publicinfo_kafka_errors_total` + аналогичные `_updated_total` / `_deleted_total`.

### Запуск

```bash
cd public-info-quarkus && mvn quarkus:dev
# Swagger: http://localhost:8095/q/swagger-ui
# Metrics: http://localhost:8095/q/metrics
```
