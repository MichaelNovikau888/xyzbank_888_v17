# Antifraud Microservice Help (Kafka Edition)

## Содержание
- [Описание модуля](#описание-модуля)
- [Функциональные возможности](#функциональные-возможности)
- [Архитектура](#архитектура)
    - [Сущности (Entities)](#сущности-entities)
    - [DTO (Data Transfer Objects)](#dto-data-transfer-objects)
    - [Репозитории (Repositories)](#репозитории-repositories)
    - [Сервисный слой (Services)](#сервисный-слой-services)
    - [Мапперы (Mappers)](#мапперы-mappers)
    - [Интеграция с Kafka](#интеграция-с-kafka)
    - [AOP-слой (Audit Logging)](#aop-слой-audit-logging)
    - [Конфигурация](#конфигурация)
- [Основные события Kafka](#основные-события-kafka)
    - [Создание подозрительного перевода](#1-создание-подозрительного-перевода)
    - [Обновление подозрительного перевода](#2-обновление-подозрительного-перевода)
    - [Удаление подозрительного перевода](#3-удаление-подозрительного-перевода)
    - [Получение подозрительных переводов](#4-получение-подозрительных-переводов)
- [Технологический стек](#технологический-стек)
- [Обработка ошибок (Exception Handling)](#обработка-ошибок-exception-handling)
- [Безопасность](#безопасность)
- [Логирование и мониторинг](#логирование-и-мониторинг)
- [Развертывание](#развертывание)
- [Дополнительные требования](#дополнительные-требования)

## Описание модуля
Antifraud Microservice предназначен для выявления и блокировки подозрительных транзакций. Основной функционал включает анализ переводов, блокировку транзакций при нарушении заданных условий и ведение истории изменений. Взаимодействие между микросервисами осуществляется через **Apache Kafka**.

## Функциональные возможности
- Взаимодействие между сервисами через **Kafka-топики**.
- Логирование изменений в таблицу аудита (только `create` и `update`).
- Автоматическое заполнение **Audit** через **AOP**.
- Интеграция с PostgreSQL.
- JWT-аутентификация.
- Автоматическая генерация документации (Swagger/OpenAPI).

## Архитектура
### Сущности (Entities)
- `SuspiciousCardTransfer` – модель подозрительных переводов по картам.
- `SuspiciousPhoneTransfer` – модель подозрительных переводов по номеру телефона.
- `SuspiciousAccountTransfer` – модель подозрительных переводов по счетам.
- `Audit` – модель записи аудита.

### DTO (Data Transfer Objects)
- `SuspiciousCardTransferDto` – передача данных о подозрительных карточных переводах.
- `SuspiciousPhoneTransferDto` – передача данных о подозрительных переводах по телефону.
- `SuspiciousAccountTransferDto` – передача данных о подозрительных переводах по счетам.
- `AuditDto` – передача информации об изменениях.

### Репозитории (Repositories)
- `SuspiciousCardTransferRepository` – управление данными подозрительных переводов по картам.
- `SuspiciousPhoneTransferRepository` – управление данными подозрительных переводов по телефону.
- `SuspiciousAccountTransferRepository` – управление данными подозрительных переводов по счетам.
- `AuditRepository` – управление аудитом.

### Сервисный слой (Services)
- `SuspiciousTransferService` – интерфейс обработки подозрительных переводов.
- `SuspiciousTransferServiceImpl` – реализация бизнес-логики.
- `AuditService` – интерфейс аудита.
- `AuditServiceImpl` – реализация логирования изменений.

### Мапперы (Mappers)
- `SuspiciousTransferMapper` – преобразует сущности подозрительных переводов в DTO и обратно.
- `AuditMapper` – преобразует сущности аудита в DTO и обратно.

### Интеграция с Kafka
- **Kafka Producer**:
    - `SuspiciousTransferProducer` – отправляет сообщения о подозрительных переводах в топики Kafka.
    - `AuditProducer` – отправляет данные аудита в Kafka.

- **Kafka Consumer**:
    - `SuspiciousTransferConsumer` – обрабатывает входящие сообщения о подозрительных переводах.
    - `AuditConsumer` – обрабатывает данные аудита.

### AOP-слой (Audit Logging)
- `AuditAspect` – аспект, перехватывающий вызовы методов `create` и `update` в `SuspiciousTransferServiceImpl`.
- Автоматически заполняет таблицу `Audit` при изменении данных о подозрительных переводах.
- Логирует, кто и когда внес изменения.
- **Для соблюдения принципа единственной ответственности (SRP), бизнес-логика выносится в сервисный слой.**
- AOP-слой отвечает только за перехват вызовов и регистрацию изменений, а сам процесс аудита выполняется в `AuditService`.

### Конфигурация
- `KafkaConfig` – конфигурация Kafka.
- `SwaggerConfig` – настройка Swagger.

## Основные события Kafka
### 1. Создание подозрительного перевода
- **Топик:** `suspicious-transfers.create`
- **Отправка:** `SuspiciousTransferProducer`
- **Обработка:** `SuspiciousTransferConsumer`
- **Действие:** Создает запись в `SuspiciousCardTransfer`, `SuspiciousPhoneTransfer` или `SuspiciousAccountTransfer` и логирует в `Audit`.

### 2. Обновление подозрительного перевода
- **Топик:** `suspicious-transfers.update`
- **Отправка:** `SuspiciousTransferProducer`
- **Обработка:** `SuspiciousTransferConsumer`
- **Действие:** Обновляет данные в `SuspiciousCardTransfer`, `SuspiciousPhoneTransfer` или `SuspiciousAccountTransfer`, фиксирует изменения в `Audit`.

### 3. Удаление подозрительного перевода
- **Топик:** `suspicious-transfers.delete`
- **Отправка:** `SuspiciousTransferProducer`
- **Обработка:** `SuspiciousTransferConsumer`
- **Действие:** Удаляет запись (без логирования в аудит).

### 4. Получение подозрительных переводов
- **Топик:** `suspicious-transfers.get`
- **Отправка:** `SuspiciousTransferProducer`
- **Обработка:** `SuspiciousTransferConsumer`
- **Действие:** Запрашивает список всех подозрительных переводов.

## Технологический стек
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL
- Lombok
- MapStruct
- Spring Security (JWT)
- **Spring Kafka**
- **Spring AOP**
- Swagger/OpenAPI
- Logback (Slf4j)

## Обработка ошибок (Exception Handling)
- `GlobalExceptionHandler` – глобальный обработчик исключений.
- Обрабатывает `EntityNotFoundException`, `ValidationException` и другие стандартные ошибки.
- Возвращает структурированные ответы с кодами ошибок.

## Безопасность
- API защищено JWT-аутентификацией.
- Логируются действия пользователей.

## Логирование и мониторинг
- Используется `Slf4j` для логирования операций.
- Детализированные сообщения об ошибках.
- Логирование успешных и неуспешных операций.

## Развертывание
- Контейнеризация через Docker.
- Kubernetes (Helm Charts).
- Конфигурация через `application.yml`.

## Дополнительные требования
- Код соответствует принципам SOLID.
- Предусмотрены unit- и интеграционные тесты.
- Готовность к CI/CD (GitLab CI/GitHub Actions).
- Kafka используется для взаимодействия между микросервисами.

---
