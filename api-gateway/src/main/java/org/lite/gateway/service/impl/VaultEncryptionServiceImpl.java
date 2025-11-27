package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.VaultEncryptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service for decrypting vault file content (read-only)
 * Uses AES-256-GCM decryption with per-environment keys derived from master key
 * 
 * Note: api-gateway only decrypts vault files. Encryption is done via vault-reader CLI.
 */
@Service
@Slf4j
public class VaultEncryptionServiceImpl implements VaultEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int KEY_LENGTH = 32; // 256 bits
    
    @Value("${vault.master.key}")
    private String masterKeyBase64;
    
    @Override
    public String decrypt(byte[] encryptedBytes, String environment) {
        try {
            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.length);
            
            // Derive environment-specific key
            SecretKeySpec envKey = deriveEnvironmentKey(environment);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, envKey, spec);
            
            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encrypted);
            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            log.debug("Decrypted vault file for environment: {}", environment);
            return plaintext;
            
        } catch (Exception e) {
            log.error("Failed to decrypt vault file for environment: {}", environment, e);
            throw new RuntimeException("Failed to decrypt vault file", e);
        }
    }
    
    /**
     * Derive environment-specific key from master key using HKDF-like approach
     */
    private SecretKeySpec deriveEnvironmentKey(String environment) {
        try {
            // Decode master key
            byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
            
            // Derive environment key using HMAC-SHA256 (HKDF-like)
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKey, "HmacSHA256");
            hmac.init(masterKeySpec);
            byte[] envKeyBytes = hmac.doFinal(environment.getBytes(StandardCharsets.UTF_8));
            
            // Ensure 256-bit key (32 bytes)
            byte[] key = new byte[KEY_LENGTH];
            System.arraycopy(envKeyBytes, 0, key, 0, Math.min(envKeyBytes.length, KEY_LENGTH));
            
            return new SecretKeySpec(key, "AES");
            
        } catch (Exception e) {
            log.error("Failed to derive environment key for: {}", environment, e);
            throw new RuntimeException("Failed to derive environment key", e);
        }
    }
}

