package com.cq.panel.authlite.filter;

import com.cq.panel.authlite.AuthContext;
import com.cq.panel.authlite.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthFilterTest {

    @Test
    void optionsRequest_passesThrough_andClearsContext() throws Exception {
        AuthFilter filter = new AuthFilter(token -> fail("should not validate token"), List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/any");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, res, (ServletRequest request, ServletResponse response) -> {
            called.set(true);
            assertNull(AuthContext.getCurrentUser());
            ((MockHttpServletResponse) response).setStatus(204);
        });

        assertTrue(called.get());
        assertEquals(204, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void ignoredPath_passesThrough_withoutAuthHeader() throws Exception {
        AuthFilter filter = new AuthFilter(token -> fail("should not validate token"), List.of("/public/**"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/public/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, res, (request, response) -> {
            called.set(true);
            ((MockHttpServletResponse) response).setStatus(200);
        });

        assertTrue(called.get());
        assertEquals(200, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void missingAuthorization_returns401() throws Exception {
        AuthFilter filter = new AuthFilter(token -> null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/secure");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> fail("should not reach chain"));

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("\"code\":401"));
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void nonBearerAuthorization_returns401() throws Exception {
        AuthFilter filter = new AuthFilter(token -> null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/secure");
        req.addHeader("Authorization", "Basic abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> fail("should not reach chain"));

        assertEquals(401, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void emptyBearerToken_returns401() throws Exception {
        AuthFilter filter = new AuthFilter(token -> null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/secure");
        req.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> fail("should not reach chain"));

        assertEquals(401, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        AuthFilter filter = new AuthFilter(token -> null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/secure");
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (request, response) -> fail("should not reach chain"));

        assertEquals(401, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }

    @Test
    void validToken_setsUserDuringChain_andClearsAfter() throws Exception {
        User user = new User("u1", Set.of("ADMIN"), Set.of("p1"));
        AuthFilter filter = new AuthFilter(token -> "ok".equals(token) ? user : null, List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/secure");
        req.addHeader("Authorization", "Bearer ok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(req, res, (request, response) -> {
            called.set(true);
            assertNotNull(AuthContext.getCurrentUser());
            assertEquals("u1", AuthContext.getCurrentUser().getUsername());
            ((MockHttpServletResponse) response).setStatus(200);
        });

        assertTrue(called.get());
        assertEquals(200, res.getStatus());
        assertNull(AuthContext.getCurrentUser());
    }
}

