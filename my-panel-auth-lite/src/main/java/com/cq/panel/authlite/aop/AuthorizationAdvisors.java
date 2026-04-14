package com.cq.panel.authlite.aop;

import com.cq.panel.authlite.AuthorizationService;
import com.cq.panel.authlite.annotation.RequirePermission;
import com.cq.panel.authlite.annotation.RequireRole;
import com.cq.panel.authlite.exception.ForbiddenException;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.core.annotation.AnnotationUtils;

public final class AuthorizationAdvisors {
    private AuthorizationAdvisors() {
    }

    public static Advisor requireRoleAdvisor(AuthorizationService authorizationService) {
        return new DefaultPointcutAdvisor(
                new AnnotationMatchingPointcut(null, RequireRole.class, true),
                (MethodInterceptor) invocation -> {
                    Method method = invocation.getMethod();
                    RequireRole anno = AnnotationUtils.findAnnotation(method, RequireRole.class);
                    if (anno == null && invocation.getThis() != null) {
                        Method targetMethod = invocation.getThis().getClass()
                                .getMethod(method.getName(), method.getParameterTypes());
                        anno = AnnotationUtils.findAnnotation(targetMethod, RequireRole.class);
                    }
                    if (anno != null && !authorizationService.hasRole(anno.value())) {
                        throw new ForbiddenException("FORBIDDEN");
                    }
                    return invocation.proceed();
                }
        );
    }

    public static Advisor requirePermissionAdvisor(AuthorizationService authorizationService) {
        return new DefaultPointcutAdvisor(
                new AnnotationMatchingPointcut(null, RequirePermission.class, true),
                (MethodInterceptor) invocation -> {
                    Method method = invocation.getMethod();
                    RequirePermission anno = AnnotationUtils.findAnnotation(method, RequirePermission.class);
                    if (anno == null && invocation.getThis() != null) {
                        Method targetMethod = invocation.getThis().getClass()
                                .getMethod(method.getName(), method.getParameterTypes());
                        anno = AnnotationUtils.findAnnotation(targetMethod, RequirePermission.class);
                    }
                    if (anno != null && !authorizationService.hasPermission(anno.value())) {
                        throw new ForbiddenException("FORBIDDEN");
                    }
                    return invocation.proceed();
                }
        );
    }
}
