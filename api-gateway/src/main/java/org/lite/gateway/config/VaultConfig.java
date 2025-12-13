package org.lite.gateway.config;

import org.lite.gateway.service.LinqraVaultService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Configuration class to ensure VaultPropertySource is created and registered
 * as early as possible in the Spring Boot lifecycle.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VaultConfig {
    
    @Bean
    public VaultPropertySource vaultPropertySource(LinqraVaultService vaultService, 
                                                   ConfigurableEnvironment environment) {
        // This will create the bean and register the PropertySource in the constructor
        return new VaultPropertySource(vaultService, environment);
    }
}

