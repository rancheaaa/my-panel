package com.cq.panel.authlite;

import java.util.Set;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    @Test
    void hasRole_returnsFalse_whenNoUser() {
        AuthContext.clear();
        AuthorizationService service = new AuthorizationService();
        assertFalse(service.hasRole("ADMIN"));
    }

    @Test
    void hasPermission_returnsFalse_whenNoUser() {
        AuthContext.clear();
        AuthorizationService service = new AuthorizationService();
        assertFalse(service.hasPermission("p1"));
    }

    @Test
    void hasRole_and_hasPermission_workWithCurrentUser() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of("ADMIN"), Set.of("p1")));
        try {
            assertTrue(service.hasRole("ADMIN"));
            assertFalse(service.hasRole("USER"));
            assertTrue(service.hasPermission("p1"));
            assertFalse(service.hasPermission("p2"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withWildcard_allResources() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("*:*:query")));
        try {
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("role:delete:query"));
            assertTrue(service.hasPermission("system:config:query"));
            assertFalse(service.hasPermission("user:add:update"));
            assertFalse(service.hasPermission("user:add"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withWildcard_moduleLevel() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("user:*:query")));
        try {
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("user:delete:query"));
            assertTrue(service.hasPermission("user:update:query"));
            assertFalse(service.hasPermission("role:delete:query"));
            assertFalse(service.hasPermission("user:add:update"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withWildcard_operationLevel() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("user:add:*")));
        try {
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("user:add:update"));
            assertTrue(service.hasPermission("user:add:delete"));
            assertFalse(service.hasPermission("user:delete:query"));
            assertFalse(service.hasPermission("role:add:query"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withMixedWildcards() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("user:*:query", "role:delete:*")));
        try {
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("user:delete:query"));
            assertTrue(service.hasPermission("role:delete:query"));
            assertTrue(service.hasPermission("role:delete:update"));
            assertFalse(service.hasPermission("role:add:query"));
            assertFalse(service.hasPermission("user:add:update"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasAnyPermission_withWildcards() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("user:*:query")));
        try {
            List<String> permissions = Arrays.asList("user:add:query", "role:delete:query");
            assertTrue(service.hasAnyPermission(permissions));

            List<String> noMatchPermissions = Arrays.asList("role:delete:query", "system:config:update");
            assertFalse(service.hasAnyPermission(noMatchPermissions));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasAllPermissions_withWildcards() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("*:*:query")));
        try {
            List<String> permissions = Arrays.asList("user:add:query", "role:delete:query");
            assertTrue(service.hasAllPermissions(permissions));

            List<String> mixedPermissions = Arrays.asList("user:add:query", "user:add:update");
            assertFalse(service.hasAllPermissions(mixedPermissions));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withSpecialCharacters() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("module.with.dots:*:query")));
        try {
            assertTrue(service.hasPermission("module.with.dots:operation:query"));
            assertFalse(service.hasPermission("modulewithdots:operation:query"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_exactMatchTakesPrecedence() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("u", Set.of(), Set.of("user:add:*", "user:add:query")));
        try {
            // 即使有通配符权限，精确匹配也应该优先工作
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("user:add:update"));
        } finally {
            AuthContext.clear();
        }
    }

    @Test
    void hasPermission_withAdminPermissions() {
        AuthorizationService service = new AuthorizationService();
        AuthContext.setCurrentUser(new User("admin", Set.of(), Set.of("*")));
        try {
            // 即使有通配符权限，精确匹配也应该优先工作
            assertTrue(service.hasPermission("user:add:query"));
            assertTrue(service.hasPermission("user:add:update"));
            assertTrue(service.hasPermission("abc"));
            assertTrue(service.hasPermission("system:user:write"));
            assertTrue(service.hasPermission("system:user:*"));
        } finally {
            AuthContext.clear();
        }
    }
}