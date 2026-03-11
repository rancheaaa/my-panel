package com.cq.panel.admin.server.common.annotation;

import java.lang.annotation.*;

/**
 * 匿名访问不鉴权注解
 * 
 * @author cq
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Anonymous
{
}
