# CONTINUATION_PROMPT_V10 — XYZ-Bank проект

## Контекст
Микросервисный банковский проект xyzbank. Spring Boot + Quarkus сервисы, Kafka, PostgreSQL, Redis.

---

## Что сделано в предыдущих чатах (полная история v1–v10)

### Чаты 1–9 (см. CONTINUATION_PROMPT_V9.md)
<вся история — antifraud, transfer, notification, JaCoCo, REVIEW статус,
Phase 1 Security Fix (JWT), Phase 2 User Registration Flow. Подробности в V9.md>

### Чат 10 — Red flag исправления + Phase 3 PaymentNotificationFormatter

#### Red flags ✅: accountNumber Long → String в 10 тест-файлах (account-service)

Проблема: тесты передавали `long` literal (`7654321L`) в `TestUtils.createAccount()` /
`createAccountDto()`, у которых третий параметр `String accountNumber` — после рефакторинга
основного кода с `Long` на `String`. Также `setAccountNumber(longLiteral)`.

Исправлены все вхождения Python-скриптом (regex):

| Файл | Замены |
|------|--------|
| `AccountServiceCreateTest.java` | 4 × `7654321L` → `"7654321"` |
| `AccountServiceDeleteTest.java` | 1 × `7654321L` → `"7654321"` |
| `AccountServiceGetByIdTest.java` | 2 × `7654321L` → `"7654321"` |
| `AccountServiceGetTest.java` | 2 × `7654321L` → `"7654321"` |
| `AccountServiceUpdateTest.java` | 5 × (`7654321L`, `8765432L`) → строки |
| `AuditServiceCreateTest.java` | 1 × `7654321L` → `"7654321"` |
| `AuditServiceUpdateTest.java` | 1 × `7654321L` → `"7654321"` |
| `AuditServiceGetAuditByEntityIdTest.java` | 1 × `7654321L` → `"7654321"` |
| `AccountValidatorImplTest.java` | 6 × `123456L` → `"123456"` |
| `CreditCardControllerIntegrationTest.java` | `setAccountNumber(4081781009991000L)` → строка |

Финальная проверка: `grep -rn "setAccountNumber.*L)"` → **ЧИСТО**.

#### Phase 3 ✅: PaymentNotificationFormatter (notification-service)

**`service/PaymentNotificationFormatter.java`** (новый):
```
formatPaymentCreatedPush(event, cardNumber, merchantName, balance)
→ "OPLATA 5000.00 RUB\nKARTA #1234\n16.04.2026 18:12:57\nGOOGLE PLAY STORE\nDostupno: 6000.00 RUB"

formatPaymentStatusPush(event, cardNumber)
→ COMPLETED: "✅ Платёж №42 выполнен: 1500.00 RUB"
→ FAILED:    "❌ Платёж №42 отклонён: KARTA #1234\nInsufficient funds"
→ CANCELLED: "🚫 Платёж №42 отменён"
→ PROCESSING: "⏳ Платёж №42 обрабатывается"
```
Package-private helpers: `maskCard()`, `formatAmount()`, `formatDateTime()`, `formatMerchant()`.
Полная защита от null — fallback без исключений.

**`service/PushNotificationService.java`** — добавлены перегрузки:
- `sendPaymentCreatedPush(..., String formattedBody)` — принимает готовый текст от formatter
- `sendPaymentStatusChangedPush(..., String formattedBody)` — аналогично
- Если `formattedBody == null` — fallback на старый формат (обратная совместимость)

**`consumer/NotificationConsumer.java`** — оба handler обновлены:
- `handlePaymentCreated` → вызывает `formatter.formatPaymentCreatedPush()` → передаёт в push
- `handlePaymentStatusChanged` → вызывает `formatter.formatPaymentStatusPush()` → передаёт в push
- cardNumber и balance пока `null` (TODO v2: запрос к account-service)

**`service/PaymentNotificationFormatterTest.java`** (новый, 13 тестов):
1. Полный формат — все поля заполнены
2. null cardNumber → `KARTA #****`
3. null merchantName → маскированный счёт получателя
4. null balance → нет строки `Dostupno`
5. COMPLETED push содержит сумму и валюту
6. FAILED push с reason
7. CANCELLED push без reason — нет `": null"`
8a-d. maskCard: 16 цифр, с пробелами, null, пустая строка
9a-c. formatAmount: целое, длинная дробь (округление), null
10. Resilience: null-поля события → fallback, не исключение

---

## Актуальная Kafka-карта

```
auth.user.registered  → notification (welcome email + push)

payment-api → payment.created              → notification (push банк. формат + email), report
payment-api → payment.status.changed       → notification (push банк. формат + email), report
payment-api → payment.antifraud.check      → antifraud
antifraud   → payment.antifraud.response   → payment-api

transfer → transfer.antifraud.check        → antifraud
antifraud → transfer.antifraud.response    → transfer (ALLOW→COMPLETED, REVIEW→REVIEW, BLOCK→BLOCKED)
transfer → transfer.events                 → history-service
transfer → transfer.notification           → notification

account → card.created/blocked/unblocked/limit.changed → notification
account ↔ authorization: auth.validate / auth.validate.response
audit.logs / error.logs → history-service
```

---

## Что нужно сделать в следующем чате

### 1. 🟡 P1 — AuthControllerTest (интеграционный, authorization-service)
Создать `src/test/java/.../controller/AuthControllerTest.java`:
- `POST /api/v1/auth/register` валидные данные → 201
- Дубль email → 409
- Невалидный email → 400
- Слабый пароль (без спецсимвола) → 400
- Короткий пароль (< 8 символов) → 400

Использовать паттерн из `CreditCardControllerIntegrationTest`:
`@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test") + @Transactional`

### 2. 🟢 P2 — NotificationConsumer: обновить тесты
`NotificationConsumerTest.java` сейчас проверяет старую сигнатуру push-методов
(без `formattedBody`). Нужно обновить verify-вызовы на новые перегрузки с 5 аргументами:
```java
// было:
verify(pushService).sendPaymentCreatedPush(clientId, paymentId, amount, currency);
// стало:
verify(pushService).sendPaymentCreatedPush(clientId, paymentId, amount, currency, anyString());
```

### 3. 🟢 P2 — JaCoCo финальный прогон
`mvn test` в `spring-boot-services/account` — убедиться что все 10 исправленных
тестов компилируются и проходят зелёными.

### 4. 🟢 P2 — PaymentNotificationFormatter v2: cardNumber из события
В `payment-api/PaymentCreatedEvent` добавить опциональное поле `cardNumber`.
В `PaymentService` передавать последние 4 цифры карты (если доступны).
Тогда `NotificationConsumer.handlePaymentCreated()` сможет передать `cardNumber`
вместо `null`.

---

## Технологический стек

- **Spring Boot**: account, antifraud, authorization, transfer, payment-api
- **Quarkus**: history-service, notification-service, report-service, profile-service, public-info
- **Kafka**: Outbox-паттерн во всех Spring Boot сервисах
- **PostgreSQL**: Liquibase (Spring Boot), Hibernate (Quarkus)
- **Redis**: notification-service (dedup TTL=24h, email cache TTL=1h, push token TTL=30d)
- **FCM**: Firebase Cloud Messaging
- **jjwt**: 0.11.5 (authorization, payment-api)
- **JaCoCo**: 0.8.12 (spring-boot-services/pom.xml)

---

## Как начать следующий чат

Приложи архив `xyzbank_v10_complete.zip` и напиши:

```
Прочитай CONTINUATION_PROMPT_V10.md внутри архива и продолжай работу.
Следующая задача: AuthControllerTest (интеграционный тест регистрации) +
обновить NotificationConsumerTest на новые сигнатуры push-методов.
```
