package com.cq.panel.admin.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger2的接口配置
 * 
 * @author cq
 */
@Configuration
public class SwaggerConfig
{
    /** 系统基础配置 */
    @Autowired
    private AppConfig appConfig;
    
    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi()
    {
        return new OpenAPI()
            .openapi("3.0.0")
            .components(new Components()
            // 设置认证的请求头
            .addSecuritySchemes("apikey", securityScheme()))
            .addSecurityItem(new SecurityRequirement().addList("apikey"))
            .info(getApiInfo());
    }
    
    @Bean
    public SecurityScheme securityScheme()
    {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .name("Authorization")
            .in(SecurityScheme.In.HEADER)
            .scheme("Bearer");
    }
    
    /**
     * 添加摘要信息
     */
    public Info getApiInfo()
    {
        return new Info()
            // 设置标题
            .title("My-Panel后台管理系统接口文档")
            // 描述
            .description("这是一个通用的后台管理系统")
            // 作者信息
            .contact(new Contact().name(appConfig.getName()))
            // 版本
            .version("版本号:" + appConfig.getVersion());
    }
}
