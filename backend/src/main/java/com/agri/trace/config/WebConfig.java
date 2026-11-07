package com.agri.trace.config;

import com.agri.trace.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web 配置：拦截器、跨域、静态资源
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private StorageProperties storageProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        // 续期接口必须免鉴权：调用它的时候 access token 往往已经过期了
                        "/auth/refresh",
                        "/auth/wx-login",
                        "/auth/wx-app-login",
                        "/auth/wx-bind",
                        "/ws",
                        "/trace/**",
                        // 官网首页的运行快照：只返回平台级聚合数字，可匿名读
                        "/public/**",
                        "/file/download/**",
                        "/doc.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/v2/api-docs",
                        "/v3/api-docs/**",
                        "/favicon.ico",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = storageProperties.getUploadDir();
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + dir);
    }
}
