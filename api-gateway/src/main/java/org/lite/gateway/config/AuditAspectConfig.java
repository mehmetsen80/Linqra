package org.lite.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for audit logging aspects
 * AOP is automatically enabled by spring-boot-starter-aop
 */
@Configuration
public class AuditAspectConfig {
    // AOP is automatically enabled by spring-boot-starter-aop dependency
    // No explicit @EnableAspectJAutoProxy needed for Spring Boot
}

