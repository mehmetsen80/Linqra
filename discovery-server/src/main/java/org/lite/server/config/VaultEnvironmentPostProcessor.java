package org.lite.server.config;

import lombok.extern.slf4j.Slf4j;
import org.lite.server.service.LinqraVaultService;
import org.lite.server.service.impl.EarlyVaultService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Registers VaultPropertySource very early in Spring Boot lifecycle,
 * before properties are resolved for SSL configuration.
 * 
 * This is the proper Spring Boot extension point for early PropertySource
 * registration.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VaultEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Use System.err for visibility (runs before logging is initialized)
        System.err.println("==========================================");
        System.err.println("VaultEnvironmentPostProcessor: STARTING");
        System.err.println("==========================================");

        try {
            log.info("VaultEnvironmentPostProcessor: Registering vault PropertySource early in lifecycle");
            String[] activeProfiles = environment.getActiveProfiles();
            System.err.println("Active profiles: "
                    + (activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "none (default)"));
            log.info("Active profiles: {}",
                    activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "none (default)");

            // Get vault configuration - try multiple sources
            String vaultMasterKey = System.getenv("VAULT_MASTER_KEY");

            // If not in environment, try reading from .env file
            if (vaultMasterKey == null || vaultMasterKey.isEmpty()) {
                System.err.println("VAULT_MASTER_KEY not in environment, checking .env file...");
                vaultMasterKey = readFromEnvFile();

                // IMPORTANT: Set it as a system property so Spring Boot can resolve
                // ${VAULT_MASTER_KEY}
                // This is needed for @Value("${vault.master.key}") which resolves to
                // ${VAULT_MASTER_KEY}
                if (vaultMasterKey != null && !vaultMasterKey.isEmpty()) {
                    System.setProperty("VAULT_MASTER_KEY", vaultMasterKey);
                    System.err.println("Set VAULT_MASTER_KEY as system property from .env file");
                }
            }

            // Also set as environment variable for this process if it wasn't already set
            if (vaultMasterKey != null && !vaultMasterKey.isEmpty() && System.getenv("VAULT_MASTER_KEY") == null) {
                // Note: We can't modify environment variables at runtime in Java,
                // but setting as system property should be sufficient for Spring Boot
            }

            // Fallback to YAML property if still not found
            if (vaultMasterKey == null || vaultMasterKey.isEmpty()) {
                vaultMasterKey = environment.getProperty("vault.master.key");
                // If it's still a placeholder like ${VAULT_MASTER_KEY}, try to resolve it
                if (vaultMasterKey != null && vaultMasterKey.startsWith("${")) {
                    String envVarName = vaultMasterKey.substring(2, vaultMasterKey.length() - 1);
                    vaultMasterKey = System.getProperty(envVarName); // Check system property first
                    if (vaultMasterKey == null || vaultMasterKey.isEmpty()) {
                        vaultMasterKey = System.getenv(envVarName);
                    }
                    // Try .env file again
                    if ((vaultMasterKey == null || vaultMasterKey.isEmpty()) && envVarName.equals("VAULT_MASTER_KEY")) {
                        vaultMasterKey = readFromEnvFile();
                        if (vaultMasterKey != null && !vaultMasterKey.isEmpty()) {
                            System.setProperty("VAULT_MASTER_KEY", vaultMasterKey);
                        }
                    }
                }
            }

            // Resolve vault file path - try YAML first, then fallback
            String vaultFilePath = environment.getProperty("vault.file.path", "/app/secrets/vault.encrypted");

            // Determine environment for vault file lookup
            String env = determineEnvironment(environment);

            // Resolve path to handle IDE vs Docker - try environment-specific file first
            if (vaultFilePath.equals("/app/secrets/vault.encrypted")) {
                String projectRoot = System.getProperty("user.dir");

                // Try environment-specific vault file first: vault-{env}.encrypted
                File envSpecificFile = new File(projectRoot, "secrets/vault-" + env + ".encrypted");
                if (envSpecificFile.exists()) {
                    vaultFilePath = envSpecificFile.getAbsolutePath();
                    System.err.println("Using environment-specific vault: " + vaultFilePath);
                } else {
                    // Fallback to generic vault.encrypted
                    File ideFile = new File(projectRoot, "secrets/vault.encrypted");
                    if (ideFile.exists()) {
                        vaultFilePath = ideFile.getAbsolutePath();
                        System.err.println("Using generic vault: " + vaultFilePath);
                    }
                }
            }

            if (vaultMasterKey == null || vaultMasterKey.isEmpty()) {
                System.err.println("ERROR: VAULT_MASTER_KEY not found! Cannot initialize vault PropertySource.");
                System.err.println("Please ensure VAULT_MASTER_KEY is set in .env file or as environment variable.");
                log.error("VAULT_MASTER_KEY not found in environment variable or vault.master.key property. " +
                        "Vault PropertySource will not be able to decrypt secrets.");
                return;
            }

            log.info("Using vault file: {}, master key: {}...", vaultFilePath,
                    vaultMasterKey.substring(0, Math.min(8, vaultMasterKey.length())));

            // Create a standalone vault service that doesn't require Spring DI
            LinqraVaultService vaultService = new EarlyVaultService(vaultFilePath, vaultMasterKey, environment);

            // Create and register PropertySource
            VaultPropertySource propertySource = new VaultPropertySource(vaultService, environment);

            MutablePropertySources propertySources = environment.getPropertySources();
            propertySources.addFirst(propertySource); // Add FIRST for highest priority

            System.err.println("VaultPropertySource registered successfully with highest priority (FIRST)");
            log.info("VaultPropertySource registered successfully with highest priority (FIRST)");

            // Test that we can read a secret
            try {
                String testSecret = vaultService.getSecret("spring.profiles.active");
                System.err.println("Vault validated successfully - retrieved test secret: " + testSecret);
                log.info("Vault validated successfully - retrieved test secret: {}", testSecret);
            } catch (Exception e) {
                System.err.println("ERROR: Vault validation failed: " + e.getMessage());
                e.printStackTrace(System.err);
                log.error("Vault validation failed during early initialization: {}", e.getMessage(), e);
                throw new RuntimeException("Vault validation failed", e);
            }

            System.err.println("==========================================");
            System.err.println("VaultEnvironmentPostProcessor: COMPLETE");
            System.err.println("==========================================");

        } catch (Exception e) {
            System.err.println("==========================================");
            System.err.println("ERROR in VaultEnvironmentPostProcessor: " + e.getMessage());
            System.err.println("==========================================");
            e.printStackTrace(System.err);
            log.error("Failed to register VaultPropertySource during early initialization: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register VaultPropertySource", e);
        }
    }

    /**
     * Determine the environment from active profiles
     */
    private String determineEnvironment(ConfigurableEnvironment environment) {
        String vaultEnv = System.getenv("VAULT_ENVIRONMENT");
        if (vaultEnv != null && !vaultEnv.isEmpty()) {
            return vaultEnv;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        String activeProfile = activeProfiles.length > 0 ? activeProfiles[0] : null;

        if (activeProfile == null) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (activeProfile == null) {
            activeProfile = System.getProperty("spring.profiles.active", "dev");
        }

        if (activeProfile.contains("prod") || activeProfile.contains("ec2")) {
            return "ec2";
        } else if (activeProfile.contains("staging")) {
            return "staging";
        } else if (activeProfile.contains("remote-dev")) {
            return "remote-dev";
        } else {
            return "dev";
        }
    }

    /**
     * Read VAULT_MASTER_KEY from .env file in project root
     */
    private String readFromEnvFile() {
        try {
            String projectRoot = System.getProperty("user.dir");
            File envFile = new File(projectRoot, ".env");

            if (!envFile.exists()) {
                System.err.println(".env file not found at: " + envFile.getAbsolutePath());
                return null;
            }

            System.err.println("Reading .env file from: " + envFile.getAbsolutePath());

            try (Stream<String> lines = Files.lines(Paths.get(envFile.getAbsolutePath()))) {
                return lines
                        .filter(line -> line.startsWith("VAULT_MASTER_KEY="))
                        .map(line -> {
                            String value = line.substring("VAULT_MASTER_KEY=".length());
                            // Remove quotes if present
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value.trim();
                        })
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException e) {
            System.err.println("Failed to read .env file: " + e.getMessage());
            return null;
        }
    }
}
