package com.bank.publicinfo.interceptor;

import com.bank.publicinfo.service.AuditService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

/**
 * CDI Interceptor — аналог Spring AOP @AfterReturning(execution(* *Impl.create/update*)).
 * Перехватывает методы с @Auditable и передаёт результат в AuditService.
 */
@Auditable
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    private static final Logger LOG = Logger.getLogger(AuditInterceptor.class);

    @Inject AuditService auditService;

    @AroundInvoke
    public Object intercept(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();
        if (result != null) {
            try {
                Auditable ann = ctx.getMethod().getAnnotation(Auditable.class);
                if (ann == null) ann = ctx.getTarget().getClass().getAnnotation(Auditable.class);
                String op = (ann != null) ? ann.operation() : "CREATE";
                if ("UPDATE".equals(op)) auditService.updateAudit(result);
                else                     auditService.createAudit(result);
            } catch (Exception e) {
                LOG.errorf(e, "AuditInterceptor failed for %s", ctx.getMethod().getName());
            }
        }
        return result;
    }
}
