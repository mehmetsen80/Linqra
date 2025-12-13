package org.lite.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.exception.SecretNotFoundException;
import org.lite.gateway.service.LinqraVaultService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.InitializingBean;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class VaultPropertySource extends EnumerablePropertySource<VaultPropertySource.VaultSource> 
        implements InitializingBean {
    
    private final LinqraVaultService vaultService;
    private final Environment environment;
    
    private final Map<String, String> cachedProperties = new HashMap<>();
    
    public VaultPropertySource(LinqraVaultService vaultService, Environment environment) {
        super("LinqraVaultPropertySource", new VaultSource());
        this.vaultService = vaultService;
        this.environment = environment;
        
        if (this.environment instanceof ConfigurableEnvironment configurableEnvironment) {
            String[] activeProfiles = environment.getActiveProfiles();
            log.info("VaultPropertySource constructor: Active profiles: {}", 
                activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "none (default)");

            org.springframework.core.env.MutablePropertySources propertySources = 
                configurableEnvironment.getPropertySources();
            propertySources.addBefore(
                "systemProperties",
                this
            );
            
            log.info("VaultPropertySource registered with high priority (from constructor)");
        }
    }
    
    @Override
    public void afterPropertiesSet() {
        try {
            vaultService.getSecret("spring.profiles.active");
            log.info("Vault validated successfully during initialization");
        } catch (Exception e) {
            String errorMsg = String.format("Failed to initialize vault: %s", e.getMessage());
            log.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
    }
    
    @Override
    public Object getProperty(@NonNull String name) {
        if (name.startsWith("vault.")) {
            if (name.startsWith("vault.file.") || name.startsWith("vault.master.")) {
                return null;
            }
            
            log.info("VaultPropertySource.getProperty() called for: {}", name);
            try {
                if (cachedProperties.containsKey(name)) {
                    log.debug("Retrieved secret from cache: {}", name);
                    return cachedProperties.get(name);
                }
                
                String key = name.substring(6);
                
                int colonIndex = key.indexOf(':');
                if (colonIndex > 0 && colonIndex < key.length() - 1) {
                    String beforeColon = key.substring(0, colonIndex).toLowerCase();
                    if (!beforeColon.matches("(http|https|bolt|file|jdbc)")) {
                        key = key.substring(0, colonIndex);
                        log.debug("Stripped default value from property name, using key: {}", key);
                    }
                }
                
                String value = vaultService.getSecret(key);
                cachedProperties.put(name, value);
                
                log.debug("Retrieved secret from vault: {}", key);
                return value;
                
            } catch (SecretNotFoundException e) {
                log.warn("Secret not found in vault: {} - {}. Will fall back to default value if available.", 
                    name, e.getMessage());
                return null;
            } catch (Exception e) {
                log.error("Failed to retrieve secret from vault: {} - {}. Will fall back to default value if available.", 
                    name, e.getMessage(), e);
                return null;
            }
        }
        
        return null;
    }
    
    @Override
    public @NonNull String[] getPropertyNames() {
        return cachedProperties.keySet().toArray(new String[0]);
    }
    
    public void invalidateCache() {
        cachedProperties.clear();
        log.debug("Vault property cache invalidated");
    }
    
    static class VaultSource {
    }
}

