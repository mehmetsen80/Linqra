package org.lite.gateway.service;

/**
 * Service for decrypting vault file content (read-only)
 * Uses AES-256-GCM decryption with per-environment keys derived from master key
 * 
 * Note: api-gateway only decrypts vault files. Encryption is done via vault-reader CLI.
 */
public interface VaultEncryptionService {
    
    /**
     * Decrypt encrypted bytes for specific environment
     * 
     * @param encryptedBytes The encrypted bytes (IV + encrypted data)
     * @param environment The environment name (dev/staging/prod)
     * @return Decrypted plaintext JSON string
     */
    String decrypt(byte[] encryptedBytes, String environment);
}

