package com.mnnu.annotation;

import java.lang.annotation.*;

/**
 * 标记此接口无需身份认证即可访问
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Anonymous {
}
