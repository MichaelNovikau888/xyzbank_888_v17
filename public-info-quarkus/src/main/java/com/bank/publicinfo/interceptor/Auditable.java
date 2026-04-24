package com.bank.publicinfo.interceptor;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Auditable {
    /** "CREATE" или "UPDATE" */
    String operation() default "CREATE";
}
