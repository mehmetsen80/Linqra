package org.lite.server.service;

/**
 * High-level service for reading vault secrets (read-only)
 * 
 * Note: discovery-server only reads from vault. Writing is done via vault-reader CLI.
 */
public interface LinqraVaultService {
    
    /**
     * Get secret value (decrypted)
     * Uses current environment automatically
     * 
     * @param key The secret key (e.g., "eureka.key.store")
     * @return The decrypted secret value
     * @throws org.lite.server.exception.SecretNotFoundException if secret not found
     */
    String getSecret(String key);
    
    /**
     * Get secret value for specific environment
     * 
     * @param key The secret key (e.g., "eureka.key.store")
     * @param environment The environment name (dev/staging/prod)
     * @return The decrypted secret value
     * @throws org.lite.server.exception.SecretNotFoundException if secret not found
     */
    String getSecret(String key, String environment);
}

