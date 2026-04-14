package com.cq.panel.authlite.aop;

import com.cq.panel.authlite.AuthContext;
import com.cq.panel.authlite.AuthorizationService;
import com.cq.panel.authlite.User;
import com.cq.panel.authlite.annotation.RequirePermission;
import com.cq.panel.authlite.annotation.RequireRole;
import com.cq.panel.authlite.exception.ForbiddenException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationAdvisorsTest {

    interface Api {
        String ping();

        String adminOnly();

        String permOnly();
    }

    static class Demo implements Api {
        @Override
        public String ping() {
            return "pong";
        }

        @RequireRole("ADMIN")
        @Override
        public String adminOnly() {
            return "ok";
        }

        @RequirePermission("p1")
        @Override
        public String permOnly() {
            return "ok";
        }
    }

    @Test
    void unannotatedMethod_isNotIntercepted() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvisor(AuthorizationAdvisors.requireRoleAdvisor(authorizationService));
        pf.addAdvisor(AuthorizationAdvisors.requirePermissionAdvisor(authorizationService));
        Demo proxy = (Demo) pf.getProxy();

        assertEquals("pong", proxy.ping());
    }

    @Test
    void requireRole_throwsForbidden_whenMissingRole() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvisor(AuthorizationAdvisors.requireRoleAdvisor(authorizationService));
        Demo proxy = (Demo) pf.getProxy();

        AuthContext.setCurrentUser(new User("u", Set.of("USER"), Set.of()));
        try {
            assertThrows(ForbiddenException.class, proxy::adminOnly);
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void requireRole_throwsForbidden_whenNoUser() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvisor(AuthorizationAdvisors.requireRoleAdvisor(authorizationService));
        Demo proxy = (Demo) pf.getProxy();

        AuthContext.clear();
        assertThrows(ForbiddenException.class, proxy::adminOnly);
    }

    @Test
    void requirePermission_throwsForbidden_whenMissingPermission() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvisor(AuthorizationAdvisors.requirePermissionAdvisor(authorizationService));
        Demo proxy = (Demo) pf.getProxy();

        AuthContext.setCurrentUser(new User("u", Set.of("ADMIN"), Set.of("p2")));
        try {
            assertThrows(ForbiddenException.class, proxy::permOnly);
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void advisors_allowInvocation_whenAuthorized() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvisor(AuthorizationAdvisors.requireRoleAdvisor(authorizationService));
        pf.addAdvisor(AuthorizationAdvisors.requirePermissionAdvisor(authorizationService));
        Demo proxy = (Demo) pf.getProxy();

        AuthContext.setCurrentUser(new User("u", Set.of("ADMIN"), Set.of("p1")));
        try {
            assertEquals("ok", proxy.adminOnly());
            assertEquals("ok", proxy.permOnly());
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void advisors_findAnnotationFromTargetMethod_whenInvocationMethodHasNoAnnotation() {
        AuthorizationService authorizationService = new AuthorizationService();
        Demo target = new Demo();
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(false);
        pf.setInterfaces(Api.class);
        pf.addAdvisor(AuthorizationAdvisors.requireRoleAdvisor(authorizationService));
        Api proxy = (Api) pf.getProxy();

        AuthContext.setCurrentUser(new User("u", Set.of("USER"), Set.of()));
        try {
            assertThrows(ForbiddenException.class, proxy::adminOnly);
        } finally {
            AuthContext.clear();
        }
    }
}
