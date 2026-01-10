package org.lite.gateway.service;

import reactor.core.publisher.Mono;

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
     * @param teamId    The team ID for key derivation
     * @return Mono emitting Base64-encoded encrypted text
     */
    Mono<String> encryptChunkText(String plaintext, String teamId);

    /**
     * Encrypt chunk text using team-specific key and specific key version.
     * 
     * @param plaintext  The plaintext to encrypt
     * @param teamId     The team ID for key derivation
     * @param keyVersion The encryption key version (e.g., "v1", "v2")
     * @return Mono emitting Base64-encoded encrypted text
     */
    Mono<String> encryptChunkText(String plaintext, String teamId, String keyVersion);

    /**
     * Decrypt chunk text using team-specific key and key version.
     * 
     * @param encryptedText The Base64-encoded encrypted text
     * @param teamId        The team ID for key derivation
     * @param keyVersion    The encryption key version used to encrypt (e.g., "v1",
     *                      "v2")
     * @return Mono emitting Decrypted plaintext
     */
    Mono<String> decryptChunkText(String encryptedText, String teamId, String keyVersion);

    /**
     * Decrypt chunk text with optional audit logging.
     * 
     * @param encryptedText The Base64-encoded encrypted text
     * @param teamId        The team ID for key derivation
     * @param keyVersion    The encryption key version used to encrypt
     * @param logAudit      Whether to log this decryption event in the audit log
     * @return Mono emitting Decrypted plaintext
     */
    Mono<String> decryptChunkText(String encryptedText, String teamId, String keyVersion, boolean logAudit);

    /**
     * Get the current encryption key version (for new encryptions).
     * 
     * @return Mono emitting Current key version (e.g., "v1", "v2")
     */
    Mono<String> getCurrentKeyVersion(String teamId);

    /**
     * Encrypt binary file data using team-specific key and current key version.
     * 
     * @param fileBytes The file bytes to encrypt
     * @param teamId    The team ID for key derivation
     * @return Mono emitting Encrypted file bytes (with IV prepended)
     */
    Mono<byte[]> encryptFile(byte[] fileBytes, String teamId);

    /**
     * Encrypt binary file data using team-specific key and specific key version.
     * 
     * @param fileBytes  The file bytes to encrypt
     * @param teamId     The team ID for key derivation
     * @param keyVersion The encryption key version (e.g., "v1", "v2")
     * @return Mono emitting Encrypted file bytes (with IV prepended)
     */
    Mono<byte[]> encryptFile(byte[] fileBytes, String teamId, String keyVersion);

    /**
     * Decrypt binary file data using team-specific key and key version.
     * 
     * @param encryptedBytes The encrypted file bytes (with IV prepended)
     * @param teamId         The team ID for key derivation
     * @param keyVersion     The encryption key version used to encrypt (e.g., "v1",
     *                       "v2")
     * @return Mono emitting Decrypted file bytes
     */
    Mono<byte[]> decryptFile(byte[] encryptedBytes, String teamId, String keyVersion);

    /**
     * Rotate encryption key for a team.
     * Generates a new random key, encrypts it with Global Master Key, and saves as
     * new version.
     * 
     * @param teamId The team ID
     * @return Mono emitting the new key version (e.g., "v2")
     */
    Mono<String> rotateKey(String teamId);
}
