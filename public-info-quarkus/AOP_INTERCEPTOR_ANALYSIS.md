# AOP в Quarkus: CDI Interceptors vs Spring AOP

## Как работает AuditInterceptor в public-info

### Механизм (@InterceptorBinding + @AroundInvoke)

```java
// 1. Маркер — аннотация-связка
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Auditable {
    String operation() default "CREATE";
}

// 2. Перехватчик — срабатывает на ЛЮБОМ методе/классе, помеченном @Auditable
@Auditable          // <── связь с маркером
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    @AroundInvoke    // <── обёртка вокруг вызова
    public Object intercept(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();     // вызываем оригинальный метод
        if (result != null) {
            String op = resolveOperation(ctx); // "CREATE" или "UPDATE"
            if ("UPDATE".equals(op)) auditService.updateAudit(result);
            else                     auditService.createAudit(result);
        }
        return result;
    }
}
```

### Аналог в Spring AOP

| Концепция Spring                        | Аналог Quarkus/CDI                          |
|-----------------------------------------|---------------------------------------------|
| `@Aspect`                               | `@Interceptor`                              |
| `@Pointcut("execution(* *Impl.*(..))")` | `@InterceptorBinding` + `@Auditable` на методе |
| `@AfterReturning`                       | `@AroundInvoke` (после `ctx.proceed()`)     |
| `@EnableAspectJAutoProxy`               | `quarkus.arc.interceptors=true` (по умолчанию) |
| `@Around`                               | `@AroundInvoke`                             |

### Где срабатывает в public-info

```
ATMServiceImpl.create(dto)        ← @Auditable           → AuditInterceptor
ATMServiceImpl.update(dto)        ← @Auditable("UPDATE") → AuditInterceptor
BankDetailsServiceImpl.create()   ← @Auditable
BankDetailsServiceImpl.update()   ← @Auditable("UPDATE")
BranchServiceImpl.create/update()
CertificateServiceImpl.create/update()
LicenseServiceImpl.create/update()
```

### Порядок выполнения (CREATE)

```
HTTP POST /api/public-info/atms
    │
    ▼
ATMResource.create(dto)
    │
    ▼  CDI прокси перехватывает вызов
AuditInterceptor.intercept(ctx)
    │
    ├── ctx.proceed()  ──────────────────────────────────────────────────────┐
    │                                                                        │
    │                    ATMServiceImpl.create(dto)                          │
    │                        ├── repository.persist(entity)   ← Tx 1        │
    │                        ├── atmProducer.sendCreated(dto) ← Kafka        │
    │                        └── return ATMDto                               │
    │                                                                        │
    │◄───────────────────────────────────────────────────────────────────────┘
    │    result = ATMDto (не null)
    │
    └── auditService.createAudit(result)
            └── @Transactional(REQUIRES_NEW) ← отдельная Tx!
                    ├── Audit.persist()
                    └── auditProducer.sendAudit()
```

---

## Конфликтует ли @Auditable с Kafka (@Incoming + @Blocking)?

### Ответ: **НЕТ. Конфликта нет.**

### Почему

CDI Interceptor срабатывает **только через CDI-прокси**. CDI-прокси создаётся только когда бин инжектируется через `@Inject`. SmallRye Reactive Messaging вызывает методы с `@Incoming` **напрямую**, минуя CDI-прокси.

```
[Kafka-поток]
    │
    ▼
SmallRye вызывает BankDetailsConsumer.onCreate(message)
    │
    │  ← @Incoming("bank-create") + @Blocking
    │  ← НЕ через CDI-прокси → interceptor НЕ срабатывает
    │
    ▼
BankDetailsConsumer.onCreate() {
    BankDetailsDto dto = parse(message);
    service.create(dto);  ← вот здесь!
}                               │
                                │ service = @Inject BankDetailsService
                                │         ← CDI-прокси!
                                ▼
                    AuditInterceptor.intercept()  ← СРАБАТЫВАЕТ ЗДЕСЬ
                        └── ctx.proceed()
                                └── BankDetailsServiceImpl.create(dto)
```

**Вывод:** `@Auditable` навешан на `BankDetailsServiceImpl.create()`, не на `BankDetailsConsumer.onCreate()`. Когда Kafka-консьюмер вызывает `service.create(dto)` через `@Inject`-инжектированный сервис — CDI-прокси работает, интерцептор срабатывает. Это корректное поведение — аудит создаётся и при Kafka-вызовах тоже.

### Таблица: когда срабатывает / не срабатывает

| Вызов                                      | CDI-прокси? | Interceptor? |
|--------------------------------------------|-------------|--------------|
| `resource.create()` → `service.create()`  | ✅ да        | ✅ да         |
| `consumer.onCreate()` → `service.create()`| ✅ да (на сервисе) | ✅ да  |
| `consumer.onCreate()` само по себе         | ❌ нет       | ❌ нет        |
| `@Incoming` метод напрямую                 | ❌ нет       | ❌ нет        |
| `this.create()` внутри того же класса     | ❌ нет       | ❌ нет        |

### Единственный реальный риск: self-invocation

```java
// ПРОБЛЕМА: this.update() обойдёт прокси
public class ATMServiceImpl {
    public ATMDto createAndUpdate(ATMDto dto) {
        ATMDto created = this.create(dto);  // ← прокси ПРОПУЩЕН!
        this.update(created);               // ← аудит НЕ запишется
        return created;
    }
}

// РЕШЕНИЕ: через @Inject self-reference
@Inject ATMService self; // инжектируем себя же

public ATMDto createAndUpdate(ATMDto dto) {
    ATMDto created = self.create(dto);  // ← через прокси → аудит работает
    self.update(created);
    return created;
}
```

В текущем коде public-info self-invocation **отсутствует** — все методы вызываются из ресурсов или консьюмеров через `@Inject`. Конфликта нет.

### @Blocking не влияет на CDI-перехват

`@Blocking` — это подсказка SmallRye перенести выполнение с event-loop потока на worker thread. Это не влияет на CDI-контекст и не отключает интерцепторы. `@ActivateRequestContext` аналогично — просто активирует CDI request scope. Оба работают совместно с `@AroundInvoke`.

---

## Итог

- `@Auditable` + `AuditInterceptor` реализует паттерн AOP через CDI-механизм Quarkus.
- Конфликта с Kafka (`@Incoming`, `@Blocking`) **нет** — интерцептор срабатывает только на бизнес-методах через CDI-прокси, Kafka-хендлеры им не являются.
- Аудит корректно записывается как при HTTP-вызовах, так и при Kafka-вызовах (через сервис).
- Единственная ловушка — self-invocation (`this.method()`) — в текущем коде отсутствует.
