package com.agri.trace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档：http://localhost:8080/api/doc.html
 * 认证方式：请求头 Authorization: Bearer {token}
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI agriTraceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("农产品质量安全溯源系统 API")
                .description("基于 FISCO-BCOS 联盟链的农产品全流程溯源接口文档")
                .version("1.0.0")
                .contact(new Contact().name("agri-origin")));
    }
}
