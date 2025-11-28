package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.VaultFile;
import org.lite.gateway.exception.SecretNotFoundException;
import org.lite.gateway.service.LinqraVaultService;
import org.springframework.core.env.Environment;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;

/**
 * Standalone vault service for early initialization (before Spring beans are created).
 * This service can decrypt and read from the vault file without requiring Spring DI.
 */
@Slf4j
public class EarlyVaultService implements LinqraVaultService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 32;
    
    private final String vaultFilePath;
    private final String masterKeyBase64;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    
    public EarlyVaultService(String vaultFilePath, String masterKeyBase64, Environment environment) {
        this.vaultFilePath = resolveVaultPath(vaultFilePath);
        this.masterKeyBase64 = masterKeyBase64;
        this.environment = environment;
        
        // Configure ObjectMapper like VaultFileServiceImpl
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
    
    @Override
    public String getSecret(String key) {
        return getSecret(key, getCurrentEnvironment());
    }
    
    @Override
    public String getSecret(String key, String env) {
        try {
            VaultFile vault = loadVault();
            VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments() != null 
                ? vault.getEnvironments().get(env) 
                : null;
                
            if (envSecrets == null || envSecrets.getSecrets() == null) {
                throw new SecretNotFoundException(key, env);
            }
            
            String value = envSecrets.getSecrets().get(key);
            if (value == null) {
                throw new SecretNotFoundException(key, env);
            }
            
            return value;
        } catch (SecretNotFoundException e) {
            // Let SecretNotFoundException propagate so VaultPropertySource can handle it gracefully
            throw e;
        } catch (Exception e) {
            log.error("Failed to get secret '{}' for environment '{}': {}", key, env, e.getMessage());
            throw new RuntimeException("Failed to get secret: " + key, e);
        }
    }
    
    private VaultFile loadVault() {
        try {
            File vaultFile = new File(vaultFilePath);
            if (!vaultFile.exists()) {
                throw new RuntimeException("Vault file not found at: " + vaultFile.getAbsolutePath());
            }
            
            String env = getCurrentEnvironment();
            byte[] encryptedBytes = Files.readAllBytes(vaultFile.toPath());
            String decryptedJson = decrypt(encryptedBytes, env);
            
            return objectMapper.readValue(decryptedJson, VaultFile.class);
        } catch (Exception e) {
            log.error("Failed to load vault file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load vault file", e);
        }
    }
    
    private String decrypt(byte[] encryptedBytes, String env) {
        try {
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.length);
            
            SecretKeySpec envKey = deriveEnvironmentKey(env);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, envKey, spec);
            
            byte[] decryptedBytes = cipher.doFinal(encrypted);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt vault file for environment: {}", env, e);
            throw new RuntimeException("Failed to decrypt vault file", e);
        }
    }
    
    private SecretKeySpec deriveEnvironmentKey(String env) {
        try {
            byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKey, "HmacSHA256");
            hmac.init(masterKeySpec);
            byte[] envKeyBytes = hmac.doFinal(env.getBytes(StandardCharsets.UTF_8));
            
            byte[] key = new byte[KEY_LENGTH];
            System.arraycopy(envKeyBytes, 0, key, 0, Math.min(envKeyBytes.length, KEY_LENGTH));
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            log.error("Failed to derive environment key for: {}", env, e);
            throw new RuntimeException("Failed to derive environment key", e);
        }
    }
    
    private String resolveVaultPath(String path) {
        File configuredFile = new File(path);
        String projectRoot = System.getProperty("user.dir");
        File ideFallbackFile = new File(projectRoot, "secrets/vault.encrypted");
        
        if (configuredFile.exists()) {
            return configuredFile.getAbsolutePath();
        }
        
        if (path.equals("/app/secrets/vault.encrypted") && ideFallbackFile.exists()) {
            log.warn("Docker vault path not found, using IDE path: {}", ideFallbackFile.getAbsolutePath());
            return ideFallbackFile.getAbsolutePath();
        }
        
        return path; // Return as-is, will fail later if not found
    }
    
    private String getCurrentEnvironment() {
        String vaultEnv = System.getenv("VAULT_ENVIRONMENT");
        if (vaultEnv != null && !vaultEnv.isEmpty()) {
            return vaultEnv;
        }
        
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null && environment != null) {
            String[] profiles = environment.getActiveProfiles();
            activeProfile = profiles.length > 0 ? profiles[0] : "dev";
        }
        if (activeProfile == null) {
            activeProfile = System.getProperty("spring.profiles.active", "dev");
        }
        
        if (activeProfile.contains("prod") || activeProfile.contains("ec2")) {
            return "ec2";
        } else if (activeProfile.contains("staging")) {
            return "staging";
        } else {
            return "dev";
        }
    }
}

