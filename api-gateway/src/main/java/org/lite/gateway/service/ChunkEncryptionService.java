package org.lite.gateway.service;

/**
 * Service for encrypting and decrypting chunk text and entity properties.
 * 
 * Uses AES-256-GCM encryption with per-team key derivation.
 * Supports multiple encryption key versions for key rotation.
 */
public interface ChunkEncryptionService {
    
    /**
     * Encrypt chunk text using team-specific key and current key version.
     * 
     * @param plaintext The plaintext to encrypt
     * @param teamId The team ID for key derivation
     * @return Base64-encoded encrypted text
     */
    String encryptChunkText(String plaintext, String teamId);
    
    /**
     * Encrypt chunk text using team-specific key and specific key version.
     * 
     * @param plaintext The plaintext to encrypt
     * @param teamId The team ID for key derivation
     * @param keyVersion The encryption key version (e.g., "v1", "v2")
     * @return Base64-encoded encrypted text
     */
    String encryptChunkText(String plaintext, String teamId, String keyVersion);
    
    /**
     * Decrypt chunk text using team-specific key and key version.
     * 
     * @param encryptedText The Base64-encoded encrypted text
     * @param teamId The team ID for key derivation
     * @param keyVersion The encryption key version used to encrypt (e.g., "v1", "v2")
     * @return Decrypted plaintext
     */
    String decryptChunkText(String encryptedText, String teamId, String keyVersion);
    
    /**
     * Get the current encryption key version (for new encryptions).
     * 
     * @return Current key version (e.g., "v1", "v2")
     */
    String getCurrentKeyVersion();
    
    /**
     * Encrypt binary file data using team-specific key and current key version.
     * 
     * @param fileBytes The file bytes to encrypt
     * @param teamId The team ID for key derivation
     * @return Encrypted file bytes (with IV prepended)
     */
    byte[] encryptFile(byte[] fileBytes, String teamId);
    
    /**
     * Encrypt binary file data using team-specific key and specific key version.
     * 
     * @param fileBytes The file bytes to encrypt
     * @param teamId The team ID for key derivation
     * @param keyVersion The encryption key version (e.g., "v1", "v2")
     * @return Encrypted file bytes (with IV prepended)
     */
    byte[] encryptFile(byte[] fileBytes, String teamId, String keyVersion);
    
    /**
     * Decrypt binary file data using team-specific key and key version.
     * 
     * @param encryptedBytes The encrypted file bytes (with IV prepended)
     * @param teamId The team ID for key derivation
     * @param keyVersion The encryption key version used to encrypt (e.g., "v1", "v2")
     * @return Decrypted file bytes
     */
    byte[] decryptFile(byte[] encryptedBytes, String teamId, String keyVersion);
}

