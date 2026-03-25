package com.mnnu.config;
/**
 * 自定义注解 - 当前用户
 */

import java.lang.annotation.*;

/**
 * 标记当前登录用户
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
