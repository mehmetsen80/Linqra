package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.entity.TeamChunkKey;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.repository.TeamChunkKeyRepository;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.LinqraVaultService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Service for encrypting and decrypting chunk text and entity properties.
 * 
 * Uses AES-256-GCM encryption with per-team key derivation.
 * Supports multiple encryption key versions for key rotation.
 */
@Slf4j
@Service
public class ChunkEncryptionServiceImpl implements ChunkEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 16; // 128 bits

    private final LinqraVaultService vaultService;
    private final TeamChunkKeyRepository teamChunkKeyRepository;
    private final AuditService auditService;
    private final UserContextService userContextService;
    private final TeamContextService teamContextService;

    public ChunkEncryptionServiceImpl(
            LinqraVaultService vaultService,
            TeamChunkKeyRepository teamChunkKeyRepository,
            @Lazy AuditService auditService,
            UserContextService userContextService,
            TeamContextService teamContextService) {
        this.vaultService = vaultService;
        this.teamChunkKeyRepository = teamChunkKeyRepository;
        this.auditService = auditService;
        this.userContextService = userContextService;
        this.teamContextService = teamContextService;
    }

    private String globalMasterKey; // The Global Master Key (v1) acts as KEK

    // Cache for team keys: key="teamId:version", value=SecretKey
    private final Map<String, Mono<SecretKey>> keyCache = new java.util.concurrent.ConcurrentHashMap<>();
    // Cache for active versions: key="teamId", value=version
    private final Map<String, Mono<String>> activeVersionCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    @PostConstruct
    public void init() {
        try {
            // Load global master key (v1) - this acts as the KEK for Team Keys
            // And also the base for legacy v1 team key derivation
            globalMasterKey = vaultService.getSecret("chunk.encryption.master.key");

            if (globalMasterKey == null || globalMasterKey.isEmpty()) {
                throw new IllegalStateException("chunk.encryption.master.key not found in vault");
            }
            log.info("Global Master Key loaded successfully");

        } catch (Exception e) {
            log.error("Failed to initialize ChunkEncryptionService. " +
                    "chunk.encryption.master.key not found in vault. " +
                    "Add it using: vault-reader --operation write --key chunk.encryption.master.key --value <base64-key>",
                    e);
            throw new IllegalStateException("chunk.encryption.master.key not found in vault. " +
                    "Add it using vault-reader CLI or bootstrap script.", e);
        }
    }

    @Override
    public Mono<String> getCurrentKeyVersion(String teamId) {
        return activeVersionCache.computeIfAbsent(teamId, tid -> teamChunkKeyRepository.findByTeamIdAndIsActiveTrue(tid)
                .map(TeamChunkKey::getVersion)
                .defaultIfEmpty("v1") // Default to v1 (legacy) if no active key found
                .cache(CACHE_TTL));
    }

    @Override
    public Mono<String> encryptChunkText(String plaintext, String teamId) {
        return getCurrentKeyVersion(teamId)
                .flatMap(version -> encryptChunkText(plaintext, teamId, version));
    }

    @Override
    public Mono<String> encryptChunkText(String plaintext, String teamId, String keyVersion) {
        if (plaintext == null || plaintext.isEmpty()) {
            return Mono.just(plaintext);
        }

        return getTeamKey(teamId, keyVersion)
                .map(secretKey -> {
                    try {
                        // Initialize cipher
                        Cipher cipher = Cipher.getInstance(ALGORITHM);
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

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
                        throw new RuntimeException("Encryption failed", e);
                    }
                })
                .onErrorMap(e -> new RuntimeException("Failed to encrypt chunk text for team: " + teamId, e));
    }

    @Override
    public Mono<String> decryptChunkText(String encryptedText, String teamId, String keyVersion) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return Mono.just(encryptedText);
        }

        // If no key version specified, assume it's unencrypted legacy data or v1
        final String version = (keyVersion == null || keyVersion.isEmpty()) ? "v1" : keyVersion;

        // Extract user and team context for audit logging
        Mono<String> usernameMono = userContextService.getCurrentUsername()
                .defaultIfEmpty(UserContextService.SYSTEM_USER);
        Mono<String> teamIdMono = teamContextService.getTeamFromContext()
                .defaultIfEmpty(teamId != null ? teamId : null)
                .onErrorResume(e -> Mono.just(teamId)); // Fallback to provided teamId

        return Mono.zip(usernameMono, teamIdMono)
                .flatMap(tuple -> {
                    String username = tuple.getT1();
                    String effectiveTeamId = tuple.getT2() != null ? tuple.getT2() : teamId;

                    return getTeamKey(teamId, version)
                            .map(secretKey -> {
                                try {
                                    // Decode Base64
                                    byte[] combined = Base64.getDecoder().decode(encryptedText);

                                    // Extract IV and encrypted data
                                    if (combined.length < GCM_IV_LENGTH) {
                                        throw new IllegalArgumentException(
                                                "Invalid encrypted data: too short to contain IV");
                                    }

                                    byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
                                    byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH,
                                            combined.length);

                                    // Initialize cipher
                                    Cipher cipher = Cipher.getInstance(ALGORITHM);
                                    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

                                    // Decrypt
                                    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
                                    return new String(decryptedBytes, StandardCharsets.UTF_8);
                                } catch (Exception e) {
                                    throw new RuntimeException("Decryption failed", e);
                                }
                            })
                            .flatMap(decrypted -> {
                                // Log successful chunk decryption (as part of reactive chain)
                                AuditLog.AuditMetadata metadata = AuditLog.AuditMetadata.builder()
                                        .reason("Chunk text decrypted successfully for team: " + teamId
                                                + " (key version: " + version + ")")
                                        .build();

                                return auditService.logEvent(
                                        username,
                                        username,
                                        effectiveTeamId,
                                        null, // ipAddress
                                        null, // userAgent
                                        AuditEventType.CHUNK_DECRYPTED,
                                        "READ",
                                        "CHUNK",
                                        null, // resourceId (chunk ID not available here)
                                        null, // documentId
                                        null, // collectionId
                                        "SUCCESS",
                                        metadata,
                                        null // complianceFlags
                                )
                                        .doOnError(error -> log.warn("Failed to log chunk decryption audit event: {}",
                                                error.getMessage()))
                                        .onErrorResume(error -> Mono.empty()) // Don't fail main operation if audit
                                                                              // logging fails
                                        .thenReturn(decrypted); // Return the decrypted text
                            })
                            .onErrorResume(error -> {
                                // Log failed chunk decryption (as part of reactive chain)
                                AuditLog.AuditMetadata metadata = AuditLog.AuditMetadata.builder()
                                        .reason("Chunk text decryption failed for team: " + teamId + " (key version: "
                                                + version + ") - " + error.getMessage())
                                        .errorMessage(error.getMessage())
                                        .build();

                                return auditService.logEvent(
                                        username,
                                        username,
                                        effectiveTeamId,
                                        null, // ipAddress
                                        null, // userAgent
                                        AuditEventType.DECRYPTION_FAILED,
                                        "READ",
                                        "CHUNK",
                                        null, // resourceId
                                        null, // documentId
                                        null, // collectionId
                                        "FAILED",
                                        metadata,
                                        null // complianceFlags
                                )
                                        .doOnError(e -> log.warn("Failed to log decryption failure audit event: {}",
                                                e.getMessage()))
                                        .onErrorResume(e -> Mono.empty()) // Don't fail if audit logging fails
                                        .then(Mono.error(error)); // Propagate the original error
                            });
                })
                .onErrorMap(e -> new RuntimeException("Failed to decrypt chunk text for team: " + teamId, e));
    }

    @Override
    public Mono<byte[]> encryptFile(byte[] fileBytes, String teamId) {
        return getCurrentKeyVersion(teamId)
                .flatMap(version -> encryptFile(fileBytes, teamId, version));
    }

    @Override
    public Mono<byte[]> encryptFile(byte[] fileBytes, String teamId, String keyVersion) {
        if (fileBytes == null || fileBytes.length == 0) {
            return Mono.just(fileBytes);
        }

        return getTeamKey(teamId, keyVersion)
                .map(secretKey -> {
                    try {
                        // Initialize cipher
                        Cipher cipher = Cipher.getInstance(ALGORITHM);
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

                        // Encrypt file bytes
                        byte[] encryptedBytes = cipher.doFinal(fileBytes);
                        byte[] iv = cipher.getIV();

                        // Combine IV + encrypted data
                        byte[] combined = new byte[iv.length + encryptedBytes.length];
                        System.arraycopy(iv, 0, combined, 0, iv.length);
                        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

                        return combined;
                    } catch (Exception e) {
                        throw new RuntimeException("Encryption failed", e);
                    }
                });
    }

    @Override
    public Mono<byte[]> decryptFile(byte[] encryptedBytes, String teamId, String keyVersion) {
        if (encryptedBytes == null || encryptedBytes.length == 0) {
            return Mono.just(encryptedBytes);
        }
        final String version = (keyVersion == null || keyVersion.isEmpty()) ? "v1" : keyVersion;

        // Extract user and team context for audit logging
        Mono<String> usernameMono = userContextService.getCurrentUsername()
                .defaultIfEmpty(UserContextService.SYSTEM_USER);
        Mono<String> teamIdMono = teamContextService.getTeamFromContext()
                .defaultIfEmpty(teamId != null ? teamId : null)
                .onErrorResume(e -> Mono.just(teamId)); // Fallback to provided teamId

        return Mono.zip(usernameMono, teamIdMono)
                .flatMap(tuple -> {
                    String username = tuple.getT1();
                    String effectiveTeamId = tuple.getT2() != null ? tuple.getT2() : teamId;

                    return getTeamKey(teamId, version)
                            .map(secretKey -> {
                                try {
                                    // Extract IV and encrypted data
                                    if (encryptedBytes.length < GCM_IV_LENGTH) {
                                        throw new IllegalArgumentException(
                                                "Invalid encrypted file: too short to contain IV");
                                    }

                                    byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, GCM_IV_LENGTH);
                                    byte[] encryptedData = Arrays.copyOfRange(encryptedBytes, GCM_IV_LENGTH,
                                            encryptedBytes.length);

                                    // Initialize cipher
                                    Cipher cipher = Cipher.getInstance(ALGORITHM);
                                    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
                                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

                                    // Decrypt file bytes
                                    return cipher.doFinal(encryptedData);
                                } catch (Exception e) {
                                    throw new RuntimeException("Decryption failed", e);
                                }
                            })
                            .flatMap(decrypted -> {
                                // Log successful file decryption (as part of reactive chain)
                                AuditLog.AuditMetadata metadata = AuditLog.AuditMetadata.builder()
                                        .reason("File decrypted successfully for team: " + teamId + " (key version: "
                                                + version + "), size: " + decrypted.length + " bytes")
                                        .build();

                                return auditService.logEvent(
                                        username,
                                        username,
                                        effectiveTeamId,
                                        null, // ipAddress
                                        null, // userAgent
                                        AuditEventType.CHUNK_DECRYPTED, // Using CHUNK_DECRYPTED for file decryption as
                                                                        // well
                                        "READ",
                                        "FILE",
                                        null, // resourceId (document ID not available here)
                                        null, // documentId
                                        null, // collectionId
                                        "SUCCESS",
                                        metadata,
                                        null // complianceFlags
                                )
                                        .doOnError(error -> log.warn("Failed to log file decryption audit event: {}",
                                                error.getMessage()))
                                        .onErrorResume(error -> Mono.empty()) // Don't fail main operation if audit
                                                                              // logging fails
                                        .thenReturn(decrypted); // Return the decrypted bytes
                            })
                            .onErrorResume(error -> {
                                // Log failed file decryption (as part of reactive chain)
                                AuditLog.AuditMetadata metadata = AuditLog.AuditMetadata.builder()
                                        .reason("File decryption failed for team: " + teamId + " (key version: "
                                                + version + ") - " + error.getMessage())
                                        .errorMessage(error.getMessage())
                                        .build();

                                return auditService.logEvent(
                                        username,
                                        username,
                                        effectiveTeamId,
                                        null, // ipAddress
                                        null, // userAgent
                                        AuditEventType.DECRYPTION_FAILED,
                                        "READ",
                                        "FILE",
                                        null, // resourceId
                                        null, // documentId
                                        null, // collectionId
                                        "FAILED",
                                        metadata,
                                        null // complianceFlags
                                )
                                        .doOnError(
                                                e -> log.warn("Failed to log file decryption failure audit event: {}",
                                                        e.getMessage()))
                                        .onErrorResume(e -> Mono.empty()) // Don't fail if audit logging fails
                                        .then(Mono.error(error)); // Propagate the original error
                            });
                });
    }

    private Mono<SecretKey> getTeamKey(String teamId, String version) {
        String cacheKey = teamId + ":" + version;
        return keyCache.computeIfAbsent(cacheKey, k -> fetchTeamKey(teamId, version).cache(CACHE_TTL));
    }

    private Mono<SecretKey> fetchTeamKey(String teamId, String version) {
        if ("v1".equals(version)) {
            return Mono.fromCallable(() -> deriveLegacyTeamKey(teamId));
        }

        // For v2+, fetch from DB and decrypt with Global Master Key
        return teamChunkKeyRepository.findByTeamIdAndVersion(teamId, version)
                .map(keyEntity -> {
                    try {
                        return decryptTeamKey(keyEntity.getEncryptedKey());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decrypt team key from storage", e);
                    }
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("Encryption key not found: " + version)));
    }

    private SecretKey deriveLegacyTeamKey(String teamId) {
        return deriveTeamKeyHmac(teamId, globalMasterKey);
    }

    private SecretKey decryptTeamKey(String encryptedTeamKeyBase64) {
        // Here we assume the stored key is just the raw bytes of the key,
        // encrypted by the Global Master Key.
        // BUT, we need to know HOW it was encrypted (IV etc).
        // For simplicity, let's assume the Global Master Key is used to AES-decrypt the
        // stored key.
        // Wait, the detailed plan said "Encrypt [new random AES key] with Global Master
        // Key".
        // It implies we need a decrypt method.
        // Similar to decryptChunkText but using GlobalMasterKey as the key.

        try {
            // Decode Base64
            byte[] combined = Base64.getDecoder().decode(encryptedTeamKeyBase64);

            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            // Prepare Master Key
            byte[] masterKeyBytes = Base64.getDecoder().decode(globalMasterKey);
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKeyBytes, "AES");

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKeySpec, spec);

            byte[] decryptedKeyBytes = cipher.doFinal(encryptedBytes);
            return new SecretKeySpec(decryptedKeyBytes, "AES");

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt team key", e);
        }
    }

    private SecretKey deriveTeamKeyHmac(String teamId, String masterKeyBase64) {
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

    @Override
    public Mono<String> rotateKey(String teamId) {
        log.info("Initiating key rotation for team: {}", teamId);
        return teamChunkKeyRepository.findAllByTeamId(teamId)
                .collectList()
                .flatMap(keys -> {
                    // 1. Determine next version
                    int maxVersion = keys.stream()
                            .map(k -> parseVersion(k.getVersion()))
                            .max(Integer::compareTo)
                            .orElse(1); // Default to v1 (legacy) -> next is v2

                    String nextVersion = "v" + (maxVersion + 1);
                    log.info("Rotating key for team {} to version {}", teamId, nextVersion);

                    // 2. Generate new key
                    SecretKey newKey = generateNewKey();
                    String encryptedKey = encryptTeamKey(newKey);

                    // 3. Deactivate old keys
                    java.util.List<TeamChunkKey> keysToUpdate = new java.util.ArrayList<>();
                    keys.stream()
                            .filter(TeamChunkKey::isActive)
                            .forEach(k -> {
                                k.setActive(false);
                                keysToUpdate.add(k);
                            });

                    // 4. Create new key entity
                    TeamChunkKey newEntity = new TeamChunkKey();
                    newEntity.setTeamId(teamId);
                    newEntity.setVersion(nextVersion);
                    newEntity.setEncryptedKey(encryptedKey);
                    newEntity.setActive(true);
                    newEntity.setCreatedAt(java.time.LocalDateTime.now());

                    // 5. Save all (Active last to ensure at least one active if possible, though
                    // strict transactional not guaranteed here across multiple objects easily
                    // without transactional operator)
                    // We save inactive ones first, then active.
                    return teamChunkKeyRepository.saveAll(keysToUpdate)
                            .then(teamChunkKeyRepository.save(newEntity))
                            .map(TeamChunkKey::getVersion)
                            .doOnSuccess(v -> {
                                // Invalidate cache
                                activeVersionCache.remove(teamId);
                                log.info("Key rotation completed for team {}. New version: {}", teamId, nextVersion);
                            });
                });
    }

    private int parseVersion(String version) {
        try {
            if (version == null || version.length() < 2)
                return 1;
            return Integer.parseInt(version.substring(1));
        } catch (Exception e) {
            return 1;
        }
    }

    private SecretKey generateNewKey() {
        try {
            javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate new key", e);
        }
    }

    private String encryptTeamKey(SecretKey teamKey) {
        try {
            // Prepare Master Key
            byte[] masterKeyBytes = Base64.getDecoder().decode(globalMasterKey);
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterKeyBytes, "AES");

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKeySpec);

            // Encrypt key bytes
            byte[] encryptedBytes = cipher.doFinal(teamKey.getEncoded());
            byte[] iv = cipher.getIV();

            // Combine IV + encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt team key", e);
        }
    }
}
