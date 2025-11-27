package org.lite.gateway.service;

/**
 * High-level service for reading vault secrets (read-only)
 * 
 * Note: api-gateway only reads from vault. Writing is done via vault-reader CLI.
 */
public interface LinqraVaultService {
    
    /**
     * Get secret value (decrypted)
     * Uses current environment automatically
     * 
     * @param key The secret key (e.g., "postgres.password")
     * @return The decrypted secret value
     * @throws org.lite.gateway.exception.SecretNotFoundException if secret not found
     */
    String getSecret(String key);
    
    /**
     * Get secret value for specific environment
     * 
     * @param key The secret key (e.g., "postgres.password")
     * @param environment The environment name (dev/staging/prod)
     * @return The decrypted secret value
     * @throws org.lite.gateway.exception.SecretNotFoundException if secret not found
     */
    String getSecret(String key, String environment);
}

