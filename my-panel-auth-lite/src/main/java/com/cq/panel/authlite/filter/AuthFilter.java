package com.cq.panel.authlite.filter;

import com.cq.panel.authlite.AuthContext;
import com.cq.panel.authlite.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

public class AuthFilter implements Filter {
    private static final String AUTHZ_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenValidator tokenValidator;
    private final List<String> ignorePatterns;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public AuthFilter(TokenValidator tokenValidator, List<String> ignorePatterns) {
        this.tokenValidator = tokenValidator;
        this.ignorePatterns = ignorePatterns;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        try {
            if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            if (isIgnored(req)) {
                chain.doFilter(request, response);
                return;
            }

            String header = req.getHeader(AUTHZ_HEADER);
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                writeUnauthorized(res);
                return;
            }
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                writeUnauthorized(res);
                return;
            }

            User user = tokenValidator.validate(token);
            if (user == null) {
                writeUnauthorized(res);
                return;
            }
            AuthContext.setCurrentUser(user);
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isIgnored(HttpServletRequest req) {
        if (ignorePatterns == null || ignorePatterns.isEmpty()) {
            return false;
        }
        String path = req.getRequestURI();
        for (String pattern : ignorePatterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse res) throws IOException {
        if (res.isCommitted()) {
            return;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":401,\"msg\":\"认证失败，无法访问系统资源\"}");
    }
}
