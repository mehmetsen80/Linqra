package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.LinqraVaultService;
import org.lite.gateway.service.VaultFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Controller for checking vault health status.
 * Used by frontend to detect vault decryption issues.
 */
@Slf4j
@RestController
@RequestMapping("/api/vault/health")
@RequiredArgsConstructor
@Tag(name = "Vault Health", description = "Check vault decryption health status")
public class VaultHealthController {

    private final LinqraVaultService vaultService;
    private final VaultFileService vaultFileService;

    @GetMapping
    @Operation(summary = "Check vault health", description = "Checks if the vault can be decrypted correctly. Returns health status indicating if vault decryption is working.")
    public Mono<ResponseEntity<VaultHealthResponse>> checkVaultHealth() {
        log.debug("Checking vault health");

        VaultHealthResponse response = new VaultHealthResponse();
        response.setTimestamp(LocalDateTime.now());

        try {
            // First, invalidate cache to ensure we're testing with the current key
            try {
                vaultFileService.invalidateCache();
            } catch (Exception e) {
                log.debug("Could not invalidate vault cache (this is okay): {}", e.getMessage());
            }

            // Resolve vault file path (same logic as LinqraVaultServiceImpl)
            String env = System.getenv("VAULT_ENVIRONMENT");
            if (env == null || env.isEmpty()) {
                env = "dev";
            }
            String vaultFileName = "vault-" + env + ".encrypted";
            Path secretsDir = Paths.get("secrets");
            Path vaultPath = secretsDir.resolve(vaultFileName);

            // Log warning if file missing (optional, but good for debugging)
            if (!Files.exists(vaultPath)) {
                log.warn("Environment specific vault file {} not found.", vaultFileName);
            }

            // Try to directly load the vault file - this will test decryption
            try {
                vaultFileService.loadVault(vaultPath);
                log.info("Vault file loaded successfully - decryption works");
            } catch (Exception e) {
                // Check if it's a decryption error
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (errorMsg.contains("decrypt") || errorMsg.contains("failed to decrypt") ||
                        errorMsg.contains("cipher") || e.getCause() instanceof javax.crypto.AEADBadTagException) {
                    log.error("Vault decryption failed - VAULT_MASTER_KEY may have changed: {}", e.getMessage());
                    response.setHealthy(false);
                    response.setStatus("UNHEALTHY");
                    response.setMessage("Vault file cannot be decrypted. " +
                            "This usually means VAULT_MASTER_KEY has changed and the vault file needs to be re-encrypted with the new key.");
                    response.setError("DecryptionFailed");
                    return Mono.just(ResponseEntity.ok(response));
                } else {
                    // Some other error (file not found, etc.)
                    throw e;
                }
            }

            // If vault loaded successfully, verify we can read at least one secret
            boolean vaultWorking = false;

            // Common secrets to try (at least one should exist)
            String[] testSecrets = {
                    "mongodb.uri",
                    "mongodb.password",
                    "postgres.password",
                    "redis.password",
                    "chunk.encryption.master.key"
            };

            for (String secretKey : testSecrets) {
                try {
                    String value = vaultService.getSecret(secretKey);
                    if (value != null && !value.isEmpty()) {
                        vaultWorking = true;
                        log.info("Vault health check passed - successfully read secret: {}", secretKey);
                        break;
                    }
                } catch (Exception e) {
                    // Continue to next secret
                    log.debug("Could not read secret {}: {}", secretKey, e.getMessage());
                }
            }

            if (vaultWorking) {
                response.setHealthy(true);
                response.setStatus("HEALTHY");
                response.setMessage("Vault can be decrypted successfully");
            } else {
                response.setHealthy(false);
                response.setStatus("UNHEALTHY");
                response.setMessage("Vault file can be decrypted but no secrets were found. " +
                        "This may indicate the vault file is empty or corrupted.");
            }

        } catch (Exception e) {
            log.error("Vault health check failed: {}", e.getMessage(), e);

            // Check if it's a decryption-related error
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            boolean isDecryptionError = errorMsg.contains("decrypt") || errorMsg.contains("failed to decrypt") ||
                    errorMsg.contains("cipher") || e.getCause() instanceof javax.crypto.AEADBadTagException;

            response.setHealthy(false);
            response.setStatus("ERROR");

            if (isDecryptionError) {
                response.setMessage("Vault decryption error: " + e.getMessage() + ". " +
                        "The VAULT_MASTER_KEY may have changed. Please re-encrypt the vault file with the new key.");
                response.setError("DecryptionFailed");
            } else {
                response.setMessage("Vault health check error: " + e.getMessage());
                response.setError(e.getClass().getSimpleName());
            }
        }

        return Mono.just(ResponseEntity.ok(response));
    }

    @Data
    public static class VaultHealthResponse {
        private boolean healthy;
        private String status; // HEALTHY, UNHEALTHY, ERROR
        private String message;
        private String error;
        private LocalDateTime timestamp;
    }
}
