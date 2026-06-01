package com.ecommerce.ordersplit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 跨域与授权拦截配置
 *
 * @author huangxinsong
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LicenseInterceptor licenseInterceptor;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    public WebConfig(LicenseInterceptor licenseInterceptor) {
        this.licenseInterceptor = licenseInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(licenseInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/license/**");
    }
}
