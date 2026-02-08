package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.VaultFile;
import org.lite.gateway.service.VaultEncryptionService;
import org.lite.gateway.service.VaultFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service for reading encrypted vault file from disk (read-only)
 * Handles file locking and caching
 * 
 * Note: api-gateway only reads from vault. Writing is done via vault-reader
 * CLI.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VaultFileServiceImpl implements VaultFileService {
    
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    
    private final VaultEncryptionService encryptionService;
    
    @Value("${vault.file.path:/app/secrets/vault.encrypted}")
    private String vaultFilePath;
    
    private VaultFile cachedVault;
    private LocalDateTime lastLoaded;
    private final java.util.concurrent.locks.ReadWriteLock fileLock = new java.util.concurrent.locks.ReentrantReadWriteLock();
    
    @Override
    public VaultFile loadVault() {
        fileLock.readLock().lock();
        try {
            // Return cached if still valid
            if (cachedVault != null && lastLoaded != null 
                    && Duration.between(lastLoaded, LocalDateTime.now()).compareTo(CACHE_TTL) < 0) {
                log.debug("Returning cached vault file (loaded {} seconds ago)", 
                        Duration.between(lastLoaded, LocalDateTime.now()).getSeconds());
                return cachedVault;
            }
            
            // Need to upgrade to write lock for loading
            fileLock.readLock().unlock();
            fileLock.writeLock().lock();
            try {
                // Double-check cache after acquiring write lock
                if (cachedVault != null && lastLoaded != null 
                        && Duration.between(lastLoaded, LocalDateTime.now()).compareTo(CACHE_TTL) < 0) {
                    return cachedVault;
                }
                
                // Load from disk
                return loadVaultFromDisk();
                
            } finally {
                fileLock.readLock().lock(); // Downgrade to read lock
                fileLock.writeLock().unlock();
            }
            
        } finally {
            fileLock.readLock().unlock();
        }
    }
    
    /**
     * Load and decrypt vault file from disk
     */
    private VaultFile loadVaultFromDisk() {
        try {
            File vaultFile = resolveVaultFile(vaultFilePath);
            
            if (!vaultFile.exists()) {
                log.warn("Vault file not found: {}. Creating empty vault.", vaultFile.getAbsolutePath());
                VaultFile emptyVault = createEmptyVault();
                cachedVault = emptyVault;
                lastLoaded = LocalDateTime.now();
                return emptyVault;
            }
            
            // Read encrypted file
            byte[] encryptedBytes = Files.readAllBytes(vaultFile.toPath());
            
            if (encryptedBytes.length == 0) {
                log.warn("Vault file is empty: {}. Creating empty vault.", vaultFile.getAbsolutePath());
                VaultFile emptyVault = createEmptyVault();
                cachedVault = emptyVault;
                lastLoaded = LocalDateTime.now();
                return emptyVault;
            }
            
            // Decrypt
            String json = encryptionService.decrypt(encryptedBytes, getCurrentEnvironment());
            
            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

            VaultFile vault = mapper.readValue(json, VaultFile.class);
            
            // Cache
            cachedVault = vault;
            lastLoaded = LocalDateTime.now();
            
            log.info("Vault file loaded successfully from: {}", vaultFile.getAbsolutePath());
            return vault;
            
        } catch (Exception e) {
            log.error("Failed to load vault file from: {}", vaultFilePath, e);
            throw new RuntimeException("Failed to load vault file", e);
        }
    }
    
    /**
     * Resolve vault file path, handling both absolute and relative paths
     * Automatically falls back between Docker path (/app/secrets/vault.encrypted)
     * and IDE path (./secrets/vault.encrypted) based on what exists
     */
    private File resolveVaultFile(String path) {
        File vaultFile = new File(path);
        
        // If absolute path (e.g., /app/secrets/vault.encrypted for Docker)
        if (vaultFile.isAbsolute()) {
            // Check if file exists - if yes, use it (Docker environment)
            if (vaultFile.exists()) {
                return vaultFile;
            }
            // If Docker path doesn't exist, try IDE path (relative to project root)
            // Convert /app/secrets/vault.encrypted -> ./secrets/vault.encrypted
            if (path.equals("/app/secrets/vault.encrypted")) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot != null) {
                    File idePath = new File(projectRoot, "secrets/vault.encrypted");
                    if (idePath.exists()) {
                        log.debug("Docker path not found, using IDE path: {}", idePath.getAbsolutePath());
                        return idePath;
                    }
                }
            }
            // Return Docker path even if it doesn't exist yet (will create empty vault)
            return vaultFile;
        }
        
        // For relative paths starting with "./", resolve against project root
        // When running from IDE, working directory is typically project root
        if (path.startsWith("./")) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot != null) {
                File projectRootDir = new File(projectRoot);
                // Remove "./" prefix and resolve relative to project root
                String relativePath = path.substring(2);
                File idePath = new File(projectRootDir, relativePath);
                
                // Check if IDE path exists - if yes, use it
                if (idePath.exists()) {
                    return idePath;
                }
                
                // If IDE path doesn't exist, try Docker path (/app/secrets/vault.encrypted)
                if (relativePath.equals("secrets/vault.encrypted")) {
                    File dockerPath = new File("/app/secrets/vault.encrypted");
                    if (dockerPath.exists()) {
                        log.debug("IDE path not found, using Docker path: {}", dockerPath.getAbsolutePath());
                        return dockerPath;
                    }
                }
                
                // Return IDE path even if it doesn't exist yet (will create empty vault)
                return idePath;
            }
        }
        
        // For other relative paths, resolve against current working directory
        return vaultFile.getAbsoluteFile();
    }
    
    @Override
    public void invalidateCache() {
        fileLock.writeLock().lock();
        try {
            cachedVault = null;
            lastLoaded = null;
            log.debug("Vault cache invalidated");
        } finally {
            fileLock.writeLock().unlock();
        }
    }
    
    /**
     * Create empty vault structure
     */
    private VaultFile createEmptyVault() {
        return VaultFile.createEmpty();
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
        
        // Map Spring profiles to environment names
        if (activeProfile.contains("prod") || activeProfile.contains("ec2")) {
            return "ec2"; // Use "ec2" directly instead of mapping to "prod"
        } else if (activeProfile.contains("staging")) {
            return "staging";
        } else if (activeProfile.contains("remote-dev")) {
            return "remote-dev";
        } else {
            return "dev";
        }
    }
}

