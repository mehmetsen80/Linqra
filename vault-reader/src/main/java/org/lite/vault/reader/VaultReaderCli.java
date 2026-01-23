package org.lite.vault.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.lite.vault.reader.dto.VaultFile;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Standalone CLI utility to read vault secrets and output as environment
 * variables
 * Usage: java -jar vault-reader.jar --file /path/to/vault.encrypted
 * --master-key <base64-key> --environment dev --format env
 */
public class VaultReaderCli {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 32;

    public static void main(String[] args) {
        try {
            Map<String, String> options = parseArgs(args);

            String vaultFile = options.get("file");
            String masterKey = options.get("master-key");
            String environment = options.getOrDefault("environment", "dev");
            String format = options.getOrDefault("format", "env");
            String key = options.get("key"); // Optional: get specific key
            String value = options.get("value"); // Optional: value for writing
            String operation = options.getOrDefault("operation", "read"); // read or write

            if (vaultFile == null || masterKey == null) {
                System.err.println("Usage:");
                System.err.println("  Read secrets:");
                System.err.println(
                        "    VaultReaderCli --file <vault-file> --master-key <base64-key> [--environment <env>] [--format <env|json>] [--key <secret-key>]");
                System.err.println("  Write secret:");
                System.err.println(
                        "    VaultReaderCli --operation write --file <vault-file> --master-key <base64-key> --environment <env> --key <secret-key> --value <secret-value>");
                System.err.println("  Create empty vault:");
                System.err
                        .println("    VaultReaderCli --operation create --file <vault-file> --master-key <base64-key>");
                System.exit(1);
            }

            if ("create".equals(operation)) {
                // Create empty vault
                createEmptyVault(vaultFile, masterKey);
                System.out.println("Empty vault created successfully: " + vaultFile);
                return;
            }

            if ("write".equals(operation)) {
                // Write secret
                if (key == null || value == null) {
                    System.err.println("Error: --key and --value are required for write operation");
                    System.exit(1);
                }
                writeSecret(vaultFile, masterKey, environment, key, value);
                System.out.println("Secret written successfully: " + key);
                return;
            }

            // Read operation (default)
            VaultFile vault = loadVault(vaultFile, masterKey, environment);

            if (key != null) {
                // Output specific key
                String secretValue = getSecret(vault, environment, key);
                if (secretValue != null) {
                    System.out.println(secretValue);
                } else {
                    System.err.println("Secret not found: " + key);
                    System.exit(1);
                }
            } else {
                // Output all secrets
                if ("json".equals(format)) {
                    outputJson(vault, environment);
                } else if ("env-file".equals(format)) {
                    outputEnvFile(vault, environment);
                } else {
                    outputEnv(vault, environment);
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length && args[i].startsWith("--")) {
                String key = args[i].substring(2);
                String value = args[i + 1];
                options.put(key, value);
            }
        }
        return options;
    }

    private static VaultFile loadVault(String vaultFilePath, String masterKeyBase64, String environment)
            throws Exception {
        File vaultFile = new File(vaultFilePath);

        if (!vaultFile.exists()) {
            throw new RuntimeException("Vault file not found: " + vaultFilePath);
        }

        // Read encrypted file
        byte[] encryptedBytes = Files.readAllBytes(vaultFile.toPath());

        if (encryptedBytes.length == 0) {
            throw new RuntimeException("Vault file is empty: " + vaultFilePath);
        }

        // Decrypt
        String json = decrypt(encryptedBytes, masterKeyBase64, environment);

        // Parse JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(json, VaultFile.class);
    }

    private static String decrypt(byte[] encryptedBytes, String masterKeyBase64, String environment) throws Exception {
        // Extract IV and encrypted data
        byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(encryptedBytes, GCM_IV_LENGTH, encryptedBytes.length);

        // Derive environment-specific key
        SecretKeySpec envKey = deriveEnvironmentKey(masterKeyBase64, environment);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, envKey, spec);

        // Decrypt
        byte[] decryptedBytes = cipher.doFinal(encrypted);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveEnvironmentKey(String masterKeyBase64, String environment) throws Exception {
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
    }

    private static String getSecret(VaultFile vault, String environment, String key) {
        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments().get(environment);
        if (envSecrets == null || envSecrets.getSecrets() == null) {
            return null;
        }
        return envSecrets.getSecrets().get(key);
    }

    /**
     * Build the key mapping from vault keys to environment variable names
     */
    private static Map<String, String> buildKeyMapping() {
        Map<String, String> keyMapping = new HashMap<>();
        keyMapping.put("mongodb.username", "MONGO_USERNAME");
        keyMapping.put("mongodb.password", "MONGO_PASSWORD");
        keyMapping.put("neo4j.username", "NEO4J_USERNAME");
        keyMapping.put("neo4j.password", "NEO4J_PASSWORD");
        keyMapping.put("milvus.username", "MILVUS_USERNAME");
        keyMapping.put("milvus.password", "MILVUS_PASSWORD");
        keyMapping.put("minio.access.key", "MINIO_ACCESS_KEY");
        keyMapping.put("minio.secret.key", "MINIO_SECRET_KEY");
        keyMapping.put("eureka.keystore.password", "EUREKA_KEY_STORE_PASSWORD");
        keyMapping.put("eureka.truststore.password", "EUREKA_TRUST_STORE_PASSWORD");
        keyMapping.put("gateway.truststore.password", "GATEWAY_TRUSTSTORE_PASSWORD");
        keyMapping.put("spring.profiles.active", "SPRING_PROFILES_ACTIVE");
        keyMapping.put("postgres.db", "POSTGRES_DB");
        keyMapping.put("postgres.user", "POSTGRES_USER");
        keyMapping.put("postgres.password", "POSTGRES_PASSWORD");
        keyMapping.put("keycloak.admin", "KEYCLOAK_ADMIN");
        keyMapping.put("keycloak.admin.password", "KEYCLOAK_ADMIN_PASSWORD");
        keyMapping.put("storage.type", "STORAGE_TYPE");
        keyMapping.put("storage.endpoint", "STORAGE_ENDPOINT");
        keyMapping.put("storage.public.endpoint", "STORAGE_PUBLIC_ENDPOINT");
        keyMapping.put("aws.access.key.id", "AWS_ACCESS_KEY_ID");
        keyMapping.put("aws.secret.access.key", "AWS_SECRET_ACCESS_KEY");
        return keyMapping;
    }

    /**
     * Output environment variables in shell export format: export VAR="value"
     */
    private static void outputEnv(VaultFile vault, String environment) {
        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments().get(environment);
        if (envSecrets == null || envSecrets.getSecrets() == null) {
            return;
        }

        Map<String, String> keyMapping = buildKeyMapping();

        for (Map.Entry<String, String> secret : envSecrets.getSecrets().entrySet()) {
            String vaultKey = secret.getKey();
            String value = secret.getValue();

            // Escape value for shell (always wrap in quotes)
            String escapedValue = value.replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");

            outputEnvironmentVariable(keyMapping, vaultKey, value, escapedValue, true);
        }
    }

    /**
     * Output environment variables in .env file format: VAR=value or VAR="value"
     * (quotes only if needed)
     */
    private static void outputEnvFile(VaultFile vault, String environment) {
        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments().get(environment);
        if (envSecrets == null || envSecrets.getSecrets() == null) {
            return;
        }

        Map<String, String> keyMapping = buildKeyMapping();

        for (Map.Entry<String, String> secret : envSecrets.getSecrets().entrySet()) {
            String vaultKey = secret.getKey();
            String value = secret.getValue();

            // Escape value for .env file format (quotes only if needed)
            String escapedValue = value;
            if (value.contains(" ") || value.contains("$") || value.contains("#")) {
                escapedValue = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\"";
            }

            outputEnvironmentVariable(keyMapping, vaultKey, value, escapedValue, false);
        }
    }

    /**
     * Helper method to output a single environment variable (reduces code
     * duplication)
     * 
     * @param keyMapping      Map of vault keys to env var names
     * @param vaultKey        The vault key
     * @param originalValue   The original value (for MongoDB special case)
     * @param escapedValue    The escaped value to output (already properly
     *                        formatted for the target format)
     * @param useExportFormat If true, output "export VAR=\"value\"", else output
     *                        "VAR=value" (escapedValue already formatted)
     */
    private static void outputEnvironmentVariable(Map<String, String> keyMapping, String vaultKey,
            String originalValue, String escapedValue, boolean useExportFormat) {
        String prefix = useExportFormat ? "export " : "";

        // Try mapped name first
        String envVar = keyMapping.get(vaultKey);
        if (envVar != null) {
            if (useExportFormat) {
                // For shell export: always wrap escapedValue in quotes
                System.out.println(prefix + envVar + "=\"" + escapedValue + "\"");
            } else {
                // For env-file: escapedValue is already correctly formatted (with or without
                // quotes)
                System.out.println(prefix + envVar + "=" + escapedValue);
            }

            // Special case: MongoDB username/password also map to MONGO_INITDB_ROOT_* for
            // docker-compose
            if ("mongodb.username".equals(vaultKey)) {
                if (useExportFormat) {
                    System.out.println(prefix + "MONGO_INITDB_ROOT_USERNAME=\"" + escapedValue + "\"");
                } else {
                    System.out.println(prefix + "MONGO_INITDB_ROOT_USERNAME=" + escapedValue);
                }
            } else if ("mongodb.password".equals(vaultKey)) {
                if (useExportFormat) {
                    System.out.println(prefix + "MONGO_INITDB_ROOT_PASSWORD=\"" + escapedValue + "\"");
                } else {
                    System.out.println(prefix + "MONGO_INITDB_ROOT_PASSWORD=" + escapedValue);
                }
            }
        } else {
            // Convert "some.key" -> "SOME_KEY"
            envVar = vaultKey.toUpperCase().replace('.', '_');
            if (useExportFormat) {
                System.out.println(prefix + envVar + "=\"" + escapedValue + "\"");
            } else {
                System.out.println(prefix + envVar + "=" + escapedValue);
            }
        }
    }

    private static void outputJson(VaultFile vault, String environment) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(System.out, vault.getEnvironments().get(environment));
    }

    /**
     * Create empty vault file
     */
    private static void createEmptyVault(String vaultFilePath, String masterKeyBase64) throws Exception {
        VaultFile emptyVault = VaultFile.builder()
                .version("1.0")
                .environments(new HashMap<>())
                .build();

        // Initialize all environments
        for (String env : new String[] { "dev", "staging", "prod" }) {
            VaultFile.EnvironmentSecrets envSecrets = VaultFile.EnvironmentSecrets.builder()
                    .updatedAt(LocalDateTime.now())
                    .updatedBy("vault-reader-cli")
                    .secrets(new HashMap<>())
                    .build();
            emptyVault.getEnvironments().put(env, envSecrets);
        }

        // Save vault
        saveVault(vaultFilePath, masterKeyBase64, "dev", emptyVault);
    }

    /**
     * Write a secret to the vault
     */
    private static void writeSecret(String vaultFilePath, String masterKeyBase64, String environment,
            String key, String value) throws Exception {
        // Load existing vault or create new one
        VaultFile vault;
        File vaultFile = new File(vaultFilePath);

        if (vaultFile.exists()) {
            vault = loadVault(vaultFilePath, masterKeyBase64, environment);
        } else {
            // Create new vault structure
            vault = VaultFile.builder()
                    .version("1.0")
                    .environments(new HashMap<>())
                    .build();

            // Initialize all environments
            for (String env : new String[] { "dev", "staging", "prod" }) {
                VaultFile.EnvironmentSecrets envSecrets = VaultFile.EnvironmentSecrets.builder()
                        .updatedAt(LocalDateTime.now())
                        .updatedBy("vault-reader-cli")
                        .secrets(new HashMap<>())
                        .build();
                vault.getEnvironments().put(env, envSecrets);
            }
        }

        // Update secret for the specific environment
        VaultFile.EnvironmentSecrets envSecrets = vault.getEnvironments().get(environment);
        if (envSecrets == null) {
            envSecrets = VaultFile.EnvironmentSecrets.builder()
                    .updatedAt(LocalDateTime.now())
                    .updatedBy("vault-reader-cli")
                    .secrets(new HashMap<>())
                    .build();
            vault.getEnvironments().put(environment, envSecrets);
        }

        if (envSecrets.getSecrets() == null) {
            envSecrets.setSecrets(new HashMap<>());
        }

        envSecrets.getSecrets().put(key, value);
        envSecrets.setUpdatedAt(LocalDateTime.now());
        envSecrets.setUpdatedBy("vault-reader-cli");

        // Save vault (save entire vault, but encrypt each environment separately)
        saveVault(vaultFilePath, masterKeyBase64, environment, vault);
    }

    /**
     * Save vault file (encrypts for the specified environment)
     */
    private static void saveVault(String vaultFilePath, String masterKeyBase64, String targetEnvironment,
            VaultFile vault) throws Exception {
        // Serialize to JSON
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(vault);

        // Encrypt
        byte[] encryptedBytes = encrypt(json, masterKeyBase64, targetEnvironment);

        // Atomic write: write to temp file, then rename
        File vaultFile = new File(vaultFilePath);
        File tempFile = new File(vaultFilePath + ".tmp");
        File backupFile = new File(vaultFilePath + ".backup");

        // Ensure directory exists
        File parentDir = vaultFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Backup existing file (if exists)
        if (vaultFile.exists()) {
            Files.copy(vaultFile.toPath(), backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // Write to temp file
        Files.write(tempFile.toPath(), encryptedBytes);

        // Atomic rename
        Files.move(tempFile.toPath(), vaultFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

        // Set file permissions (600: owner read/write only)
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(vaultFile.toPath(), permissions);
        } catch (UnsupportedOperationException e) {
            // Windows doesn't support POSIX permissions, ignore
        }
    }

    /**
     * Encrypt plaintext for specific environment
     */
    private static byte[] encrypt(String plaintext, String masterKeyBase64, String environment) throws Exception {
        // Derive environment-specific key
        SecretKeySpec envKey = deriveEnvironmentKey(masterKeyBase64, environment);

        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, envKey);

        // Encrypt
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();

        // Combine IV + encrypted data
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        return combined;
    }
}
