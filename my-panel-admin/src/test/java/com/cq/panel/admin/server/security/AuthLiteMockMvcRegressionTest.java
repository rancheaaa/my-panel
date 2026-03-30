package com.cq.panel.admin.server.security;

import com.cq.panel.authlite.User;
import com.cq.panel.authlite.annotation.RequirePermission;
import com.cq.panel.authlite.filter.TokenValidator;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthLiteMockMvcRegressionTest.TestAuthConfig.class)
class AuthLiteMockMvcRegressionTest {

    @TestConfiguration
    static class TestAuthConfig {
        private static final class AllPermissionsSet extends AbstractSet<String> {
            @Override
            public boolean contains(Object o) {
                return o instanceof String;
            }

            @Override
            public Iterator<String> iterator() {
                return Set.<String>of().iterator();
            }

            @Override
            public int size() {
                return 0;
            }
        }

        @Bean
        @Primary
        public TokenValidator tokenValidator() {
            return token -> {
                if ("admin".equals(token)) {
                    return new User("admin", Set.of("ADMIN"), new AllPermissionsSet());
                }
                if ("noperm".equals(token)) {
                    return new User("user", Set.of("USER"), Set.of());
                }
                return null;
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void regression_on_atLeast_30_protectedGetEndpoints() throws Exception {
        List<String> endpoints = collectProtectedGetEndpoints();
        assertTrue(endpoints.size() >= 30, "protected GET endpoints < 30, actual=" + endpoints.size());

        List<String> selected = endpoints.subList(0, 30);
        for (String url : selected) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get(url).header("Authorization", "Bearer noperm"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get(url).header("Authorization", "Bearer admin"))
                    .andExpect(status().isOk());
        }
    }

    private List<String> collectProtectedGetEndpoints() {
        Pattern pathVar = Pattern.compile("\\{([^/}]+)}");
        List<String> safe = new ArrayList<>();
        List<String> others = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            org.springframework.web.method.HandlerMethod hm = e.getValue();

            RequirePermission rp = AnnotationUtils.findAnnotation(hm.getMethod(), RequirePermission.class);
            if (rp == null) {
                continue;
            }

            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (!methods.contains(RequestMethod.GET)) {
                continue;
            }

            for (String pattern : info.getPatternValues()) {
                String url = materializeUrl(pattern, pathVar);
                if (url.contains("/list") || url.contains("/get")) {
                    safe.add(url);
                } else {
                    others.add(url);
                }
            }
        }

        safe.sort(Comparator.naturalOrder());
        others.sort(Comparator.naturalOrder());

        List<String> result = new ArrayList<>(safe);
        for (String u : others) {
            if (!result.contains(u)) {
                result.add(u);
            }
        }
        return result;
    }

    private String materializeUrl(String pattern, Pattern pathVar) {
        Matcher m = pathVar.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String replacement = "1";
            if ("userName".equalsIgnoreCase(name)) {
                replacement = "admin";
            }
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
