package com.bank.profile.interceptor;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Маркерная аннотация для CDI-перехватчика аудита.
 * Аналог Spring AOP @AfterReturning — навешивается на методы сервиса.
 * operation(): "CREATE" (по умолчанию) или "UPDATE".
 /
 * Пример использования:
 *   @Auditable(operation = "CREATE")
 *   public ProfileDto create(ProfileDto dto) { ... }
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Auditable {
    // "CREATE" или "UPDATE"
    String operation() default "CREATE";
}
