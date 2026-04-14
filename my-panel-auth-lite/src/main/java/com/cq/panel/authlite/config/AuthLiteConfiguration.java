package com.cq.panel.authlite.config;

import com.cq.panel.authlite.AuthorizationService;
import com.cq.panel.authlite.aop.AuthorizationAdvisors;
import org.springframework.aop.Advisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class AuthLiteConfiguration {
    
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AuthorizationService authorizationService() {
        return new AuthorizationService();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor requireRoleAdvisor(AuthorizationService authorizationService) {
        return AuthorizationAdvisors.requireRoleAdvisor(authorizationService);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor requirePermissionAdvisor(AuthorizationService authorizationService) {
        return AuthorizationAdvisors.requirePermissionAdvisor(authorizationService);
    }
}