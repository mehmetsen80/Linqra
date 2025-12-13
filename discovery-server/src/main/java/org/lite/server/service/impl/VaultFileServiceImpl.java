package org.lite.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.server.dto.VaultFile;
import org.lite.server.service.VaultEncryptionService;
import org.lite.server.service.VaultFileService;
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
 * Note: discovery-server only reads from vault. Writing is done via vault-reader CLI.
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
            
            // If resolved file doesn't exist, throw an exception - don't create empty vault
            // An empty vault would cause property resolution to fail silently
            if (!vaultFile.exists()) {
                String projectRoot = System.getProperty("user.dir");
                String errorMsg = String.format(
                    "Vault file not found! Searched at: %s (configured path: %s). Project root: %s. " +
                    "Please ensure the vault file exists at ./secrets/vault.encrypted (for IDE) or " +
                    "/app/secrets/vault.encrypted (for Docker). Run 'source ./scripts/bootstrap-vault.sh ./secrets/secrets.json dev' to create it.",
                    vaultFile.getAbsolutePath(), vaultFilePath, projectRoot);
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
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
            
            // Get environment for decryption
            String environment = getCurrentEnvironment();
            log.debug("Decrypting vault file using environment: {}", environment);
            
            // Decrypt
            String json = encryptionService.decrypt(encryptedBytes, environment);
            
            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            
            VaultFile vault = mapper.readValue(json, VaultFile.class);
            
            // Log available environments and secrets for debugging
            if (vault.getEnvironments() != null) {
                log.debug("Vault contains environments: {}", vault.getEnvironments().keySet());
                VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments().get(environment);
                if (envSecrets != null && envSecrets.getSecrets() != null) {
                    log.info("Vault loaded successfully from: {} (environment: {}, {} secrets)", 
                        vaultFile.getAbsolutePath(), environment, envSecrets.getSecrets().size());
                } else {
                    log.warn("Vault file loaded but environment '{}' not found or has no secrets. Available environments: {}", 
                        environment, vault.getEnvironments().keySet());
                }
            } else {
                log.warn("Vault file loaded but contains no environments");
            }
            
            // Cache
            cachedVault = vault;
            lastLoaded = LocalDateTime.now();
            
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
        log.debug("Resolving vault file path from configured path: {}", path);
        File vaultFile = new File(path);
        
        // If absolute path (e.g., /app/secrets/vault.encrypted for Docker)
        if (vaultFile.isAbsolute()) {
            // Check if file exists - if yes, use it (Docker environment)
            if (vaultFile.exists()) {
                log.info("Using Docker vault path (exists): {}", vaultFile.getAbsolutePath());
                return vaultFile;
            }
            // If Docker path doesn't exist, try IDE path (relative to project root)
            // Convert /app/secrets/vault.encrypted -> ./secrets/vault.encrypted
            if (path.equals("/app/secrets/vault.encrypted")) {
                String projectRoot = System.getProperty("user.dir");
                log.debug("Docker path not found at: {}, trying IDE path. Project root (user.dir): {}", path, projectRoot);
                
                // Try multiple potential IDE locations
                String[] possiblePaths = {
                    projectRoot != null ? new File(projectRoot, "secrets/vault.encrypted").getAbsolutePath() : null,
                    "secrets/vault.encrypted",
                    "./secrets/vault.encrypted"
                };
                
                for (String possiblePath : possiblePaths) {
                    if (possiblePath == null) continue;
                    
                    File testPath = new File(possiblePath);
                    if (testPath.exists()) {
                        log.info("Docker path not found, using IDE path: {}", testPath.getAbsolutePath());
                        return testPath;
                    } else {
                        log.debug("IDE path also not found at: {}", testPath.getAbsolutePath());
                    }
                }
                
                log.warn("Neither Docker nor IDE vault path found. Tried: {} and various IDE locations. Will attempt to create empty vault at: {}", 
                    path, vaultFile.getAbsolutePath());
            }
            // Return Docker path even if it doesn't exist yet (will create empty vault)
            return vaultFile;
        }
        
        // For relative paths starting with "./", resolve against project root
        // When running from IDE, working directory is typically project root
        if (path.startsWith("./")) {
            String projectRoot = System.getProperty("user.dir");
            log.debug("Resolving relative path: {} from project root: {}", path, projectRoot);
            if (projectRoot != null) {
                File projectRootDir = new File(projectRoot);
                // Remove "./" prefix and resolve relative to project root
                String relativePath = path.substring(2);
                File idePath = new File(projectRootDir, relativePath);
                
                // Check if IDE path exists - if yes, use it
                if (idePath.exists()) {
                    log.info("Using IDE vault path (exists): {}", idePath.getAbsolutePath());
                    return idePath;
                }
                
                // If IDE path doesn't exist, try Docker path (/app/secrets/vault.encrypted)
                if (relativePath.equals("secrets/vault.encrypted")) {
                    File dockerPath = new File("/app/secrets/vault.encrypted");
                    if (dockerPath.exists()) {
                        log.info("IDE path not found, using Docker path: {}", dockerPath.getAbsolutePath());
                        return dockerPath;
                    } else {
                        log.warn("Docker path also not found at: {}. Will attempt to create empty vault at IDE path: {}", 
                            dockerPath.getAbsolutePath(), idePath.getAbsolutePath());
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
        } else {
            return "dev";
        }
    }
    
}

