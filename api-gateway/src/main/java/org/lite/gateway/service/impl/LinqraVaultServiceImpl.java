package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.VaultFile;
import org.lite.gateway.exception.SecretNotFoundException;
import org.lite.gateway.service.LinqraVaultService;
import org.lite.gateway.service.VaultFileService;
import org.springframework.stereotype.Service;


/**
 * High-level service for reading vault secrets (read-only)
 * 
 * Note: api-gateway only reads from vault. Writing is done via vault-reader CLI.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LinqraVaultServiceImpl implements LinqraVaultService {
    
    private final VaultFileService vaultFileService;
    
    @Override
    public String getSecret(String key) {
        String environment = getCurrentEnvironment();
        return getSecret(key, environment);
    }
    
    @Override
    public String getSecret(String key, String environment) {
        VaultFile vault = vaultFileService.loadVault();
        
        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments() != null 
            ? vault.getEnvironments().get(environment) 
            : null;
            
        if (envSecrets == null || envSecrets.getSecrets() == null) {
            throw new SecretNotFoundException(key, environment);
        }
        
        String value = envSecrets.getSecrets().get(key);
        if (value == null) {
            throw new SecretNotFoundException(key, environment);
        }
        
        log.debug("Retrieved secret: {} for environment: {}", key, environment);
        return value;
    }
    
    /**
     * Get current environment (dev/dev-docker/staging/prod/ec2)
     * Priority:
     * 1. VAULT_ENVIRONMENT environment variable (if set, use directly)
     * 2. Spring profile mapping (dev → dev, ec2 → ec2, etc.)
     */
    private String getCurrentEnvironment() {
        // Check VAULT_ENVIRONMENT first (allows explicit override)
        String vaultEnv = System.getenv("VAULT_ENVIRONMENT");
        if (vaultEnv != null && !vaultEnv.isEmpty()) {
            return vaultEnv;
        }
        
        // Fall back to Spring profile mapping
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null) {
            activeProfile = System.getProperty("spring.profiles.active", "dev");
        }
        
        if (activeProfile.contains("prod") || activeProfile.contains("ec2")) {
            return "ec2"; // Use "ec2" directly instead of mapping to "prod"
        } else if (activeProfile.contains("staging")) {
            return "staging";
        } else {
            return "dev";
        }
    }
    
}

