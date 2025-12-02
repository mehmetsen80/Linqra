package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.exception.SecretNotFoundException;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.LinqraVaultService;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for encrypting and decrypting chunk text and entity properties.
 * 
 * Uses AES-256-GCM encryption with per-team key derivation.
 * Supports multiple encryption key versions for key rotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkEncryptionServiceImpl implements ChunkEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    
    private final LinqraVaultService vaultService;
    
    private String currentKeyVersion = "v1"; // Default version
    private final Map<String, String> keyVersions = new HashMap<>(); // version -> base64 key
    
    @PostConstruct
    public void init() {
        try {
            // Load current master key (default version)
            String masterKey = vaultService.getSecret("chunk.encryption.master.key");
            keyVersions.put("v1", masterKey);
            log.info("Loaded encryption key version v1");
            
            // Check for newer key versions (e.g., v2, v3)
            // Format: chunk.encryption.master.key.v2, chunk.encryption.master.key.v3
            int version = 2;
            while (true) {
                String keyName = "chunk.encryption.master.key.v" + version;
                try {
                    String versionedKey = vaultService.getSecret(keyName);
                    if (versionedKey != null && !versionedKey.isEmpty()) {
                        keyVersions.put("v" + version, versionedKey);
                        currentKeyVersion = "v" + version; // Update to latest version
                        log.info("Loaded encryption key version v{}", version);
                        version++;
                    } else {
                        break;
                    }
                } catch (SecretNotFoundException e) {
                    // No more versions found
                    break;
                }
            }
            
            log.info("Loaded encryption key versions: {}", keyVersions.keySet());
            log.info("Current encryption key version: {}", currentKeyVersion);
            
        } catch (Exception e) {
            log.error("Failed to initialize ChunkEncryptionService. " +
                "chunk.encryption.master.key not found in vault. " +
                "Add it using: vault-reader --operation write --key chunk.encryption.master.key --value <base64-key>", e);
            throw new IllegalStateException("chunk.encryption.master.key not found in vault. " +
                "Add it using vault-reader CLI or bootstrap script.", e);
        }
    }
    
    @Override
    public String getCurrentKeyVersion() {
        return currentKeyVersion;
    }
    
    @Override
    public String encryptChunkText(String plaintext, String teamId) {
        return encryptChunkText(plaintext, teamId, currentKeyVersion);
    }
    
    @Override
    public String encryptChunkText(String plaintext, String teamId, String keyVersion) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            String masterKeyBase64 = keyVersions.get(keyVersion);
            if (masterKeyBase64 == null) {
                throw new IllegalStateException("Encryption key version not found: " + keyVersion);
            }
            
            // Derive team-specific key
            SecretKey teamKey = deriveTeamKey(teamId, masterKeyBase64);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, teamKey);
            
            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            
            // Combine IV + encrypted data (GCM includes tag)
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            // Return Base64 encoded
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            log.error("Failed to encrypt chunk text for team: {}, version: {}", teamId, keyVersion, e);
            throw new RuntimeException("Failed to encrypt chunk text", e);
        }
    }
    
    @Override
    public String decryptChunkText(String encryptedText, String teamId, String keyVersion) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        if (keyVersion == null || keyVersion.isEmpty()) {
            keyVersion = "v1"; // Default to v1 for legacy data
        }
        
        try {
            String masterKeyBase64 = keyVersions.get(keyVersion);
            if (masterKeyBase64 == null) {
                throw new IllegalStateException("Encryption key version not found: " + keyVersion + 
                    ". Cannot decrypt chunk. Key may have been rotated. Available versions: " + keyVersions.keySet());
            }
            
            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted data
            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data: too short to contain IV");
            }
            
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            
            // Derive team-specific key
            SecretKey teamKey = deriveTeamKey(teamId, masterKeyBase64);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, teamKey, spec);
            
            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (IllegalStateException e) {
            // Re-throw IllegalStateException (version not found)
            throw e;
        } catch (IllegalArgumentException e) {
            // Re-throw IllegalArgumentException (invalid data format)
            throw e;
        } catch (javax.crypto.AEADBadTagException e) {
            // Wrong key or corrupted data - most common decryption failure
            String errorMsg = String.format(
                "Decryption authentication failed for team: %s, version: %s. " +
                "This usually means: 1) Wrong encryption key, 2) Data was encrypted with a different key version, " +
                "or 3) Corrupted encrypted data. Error: %s",
                teamId, keyVersion, e.getMessage()
            );
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorDetails = String.format(
                "Failed to decrypt chunk text for team: %s, version: %s. Error type: %s, Message: %s",
                teamId, keyVersion, e.getClass().getSimpleName(), e.getMessage()
            );
            log.error(errorDetails, e);
            throw new RuntimeException(errorDetails, e);
        }
    }
    
    @Override
    public byte[] encryptFile(byte[] fileBytes, String teamId) {
        return encryptFile(fileBytes, teamId, currentKeyVersion);
    }
    
    @Override
    public byte[] encryptFile(byte[] fileBytes, String teamId, String keyVersion) {
        if (fileBytes == null || fileBytes.length == 0) {
            return fileBytes;
        }
        
        try {
            String masterKeyBase64 = keyVersions.get(keyVersion);
            if (masterKeyBase64 == null) {
                throw new IllegalStateException("Encryption key version not found: " + keyVersion);
            }
            
            // Derive team-specific key
            SecretKey teamKey = deriveTeamKey(teamId, masterKeyBase64);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, teamKey);
            
            // Encrypt file bytes
            byte[] encryptedBytes = cipher.doFinal(fileBytes);
            byte[] iv = cipher.getIV();
            
            // Combine IV + encrypted data (GCM includes tag)
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return combined;
            
        } catch (Exception e) {
            log.error("Failed to encrypt file for team: {}, version: {}", teamId, keyVersion, e);
            throw new RuntimeException("Failed to encrypt file", e);
        }
    }
    
    @Override
    public byte[] decryptFile(byte[] encryptedBytes, String teamId, String keyVersion) {
        if (encryptedBytes == null || encryptedBytes.length == 0) {
            return encryptedBytes;
        }
        
        if (keyVersion == null || keyVersion.isEmpty()) {
            keyVersion = "v1"; // Default to v1 for legacy data
        }
        
        try {
            String masterKeyBase64 = keyVersions.get(keyVersion);
            if (masterKeyBase64 == null) {
                throw new IllegalStateException("Encryption key version not found: " + keyVersion + 
                    ". Cannot decrypt file. Key may have been rotated. Available versions: " + keyVersions.keySet());
            }
            
            // Extract IV and encrypted data
            if (encryptedBytes.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted file: too short to contain IV");
            }
            
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, GCM_IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.length);
            
            // Derive team-specific key
            SecretKey teamKey = deriveTeamKey(teamId, masterKeyBase64);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, teamKey, spec);
            
            // Decrypt file bytes
            return cipher.doFinal(encryptedData);
            
        } catch (IllegalStateException e) {
            // Re-throw IllegalStateException (version not found)
            throw e;
        } catch (Exception e) {
            log.error("Failed to decrypt file for team: {}, version: {}", teamId, keyVersion, e);
            throw new RuntimeException("Failed to decrypt file for team: " + teamId + 
                " with key version: " + keyVersion, e);
        }
    }
    
    /**
     * Derive team-specific encryption key from master key using HMAC-SHA256.
     * 
     * @param teamId The team ID
     * @param masterKeyBase64 The base64-encoded master key
     * @return SecretKey for AES encryption
     */
    private SecretKey deriveTeamKey(String teamId, String masterKeyBase64) {
        try {
            // Decode master key
            byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
            
            // Derive team key using HMAC-SHA256
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKey, "HmacSHA256");
            hmac.init(masterKeySpec);
            byte[] teamKeyBytes = hmac.doFinal(teamId.getBytes(StandardCharsets.UTF_8));
            
            return new SecretKeySpec(teamKeyBytes, "AES");
            
        } catch (Exception e) {
            log.error("Failed to derive team key for team: {}", teamId, e);
            throw new RuntimeException("Failed to derive team key", e);
        }
    }
}

