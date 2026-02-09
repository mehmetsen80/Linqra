package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.lite.gateway.dto.VaultFile;
import org.lite.gateway.exception.SecretNotFoundException;
import org.lite.gateway.service.LinqraVaultService;
import org.lite.gateway.service.VaultFileService;
import org.springframework.stereotype.Service;

/**
 * High-level service for reading vault secrets (read-only)
 * 
 * Note: api-gateway only reads from vault. Writing is done via vault-reader
 * CLI.
 */
@Service
@Slf4j
public class LinqraVaultServiceImpl implements LinqraVaultService {

    private final VaultFileService vaultFileService;
    private final Path vaultFile;

    public LinqraVaultServiceImpl(VaultFileService vaultFileService) {
        this.vaultFileService = vaultFileService;

        String env = getCurrentEnvironment();

        // Construct vault file path based on environment: secrets/vault-{env}.encrypted
        String vaultFileName = "vault-" + env + ".encrypted";
        Path secretsDir = Paths.get("secrets");
        Path specificFile = secretsDir.resolve(vaultFileName);

        log.info("LinqraVaultServiceImpl initialized. Detected environment: {}, Loading file: {}", env,
                specificFile.toAbsolutePath());

        // Fallback to legacy vault.encrypted if specific file doesn't exist
        if (!Files.exists(specificFile)) {
            // ... (keep existing logic)
            // But actually we removed fallback logic in previous steps, so this comment was
            // misleading in the Context I saw?
            // Wait, looking at file content in Step 12195, the fallback logic IS THERE.
            // Ah, I see "Fallback to legacy vault.encrypted if specific file doesn't
            // exist".
            // I thought I removed it? Maybe I removed it in Controller but the Service edit
            // failed or I missed it?
            // Let's re-verify Step 12030...
            // Step 12030 had replace_file_content for LinqraVaultServiceImpl.
            // But it seems it might have been reverted or I am misreading the current file
            // content provided in 12195.
            // In 12195 lines 42-52 clearly show the fallback logic.
            // So my previous attempt to remove it might have failed or been overwritten?
            // User asked to remove it in Step 12027.
            // I will remove it NOW to be sure and consistent.
        }

        // Actually, let me just add the log first. I will handle the fallback removal
        // separately if needed, to keep this atomic.
        // But wait, if I am editing this file anyway, I should probably respect the
        // user's previous wish to remove fallback if it's still there.
        // The file content in 12195 DEFINITELY has fallback logic.
        // I will remove the fallback logic AND add the log.

        if (!Files.exists(specificFile)) {
            log.warn("Environment specific vault file {} not found.", vaultFileName);
        }

        this.vaultFile = specificFile;
        log.info("Initializing Vault Service with file: {}", vaultFile.toAbsolutePath());
    }

    @Override
    public String getSecret(String key) {
        String environment = getCurrentEnvironment();
        return getSecret(key, environment);
    }

    @Override
    public String getSecret(String key, String environment) {

        VaultFile vault = vaultFileService.loadVault(this.vaultFile);

        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments() != null
                ? vault.getEnvironments().get(environment)
                : null;

        if (envSecrets == null || envSecrets.getSecrets() == null) {
            throw new SecretNotFoundException(key, environment);
        }

        String value = envSecrets.getSecrets().get(key);
        if (value == null) {
            throw new SecretNotFoundException(key, environment);
        }

        log.debug("Retrieved secret: {} for environment: {}", key, environment);
        return value;
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

        String detectedEnv;

        if (activeProfile.contains("prod") || activeProfile.contains("ec2")) {
            detectedEnv = "ec2";
        } else if (activeProfile.contains("staging")) {
            detectedEnv = "staging";
        } else if (activeProfile.contains("remote-dev")) {
            detectedEnv = "remote-dev";
        } else {
            detectedEnv = "dev";
        }

        return detectedEnv;
    }

}
