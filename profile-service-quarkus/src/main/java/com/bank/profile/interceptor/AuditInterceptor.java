package com.bank.profile.interceptor;

import com.bank.profile.service.AuditService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

/**
 * CDI Interceptor — аналог Spring AOP @AfterReturning.
 * Перехватывает методы с @Auditable и передаёт результат в AuditService.
 /
 * Отличия от Spring-варианта:
 *   Spring: @Aspect + @AfterReturning(pointcut, returning="result")
 *   Quarkus: @InterceptorBinding + @Interceptor + @AroundInvoke
 /
 * Регистрация в CDI происходит автоматически через @Interceptor.
 * Порядок: APPLICATION (1000) — выполняется после транзакционных interceptors.
 /
 * Ошибки аудита не прерывают бизнес-операцию (catch + log).
 */
@Auditable
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    private static final Logger LOG = Logger.getLogger(AuditInterceptor.class);

    @Inject
    AuditService auditService;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();
        if (result != null) {
            try {
                Auditable ann = ctx.getMethod().getAnnotation(Auditable.class);
                if (ann == null) {
                    ann = ctx.getTarget().getClass().getAnnotation(Auditable.class);
                }
                String op = (ann != null) ? ann.operation() : "CREATE";
                if ("UPDATE".equals(op)) {
                    auditService.update(result);
                } else {
                    auditService.create(result);
                }
            } catch (Exception e) {
                LOG.errorf(e, "AuditInterceptor failed for method=%s", ctx.getMethod().getName());
            }
        }
        return result;
    }
}
