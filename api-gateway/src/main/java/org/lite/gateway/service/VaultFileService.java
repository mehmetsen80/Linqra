package org.lite.gateway.service;

import java.nio.file.Path;

import org.lite.gateway.dto.VaultFile;

/**
 * Service for reading encrypted vault file from disk (read-only)
 * Handles file locking and caching
 * 
 * Note: api-gateway only reads from vault. Writing is done via vault-reader
 * CLI.
 */
public interface VaultFileService {

    /**
     * Load vault file from disk and decrypt
     * Returns cached version if still valid (5-minute TTL)
     * 
     * @return Decrypted VaultFile
     */
    VaultFile loadVault(Path vaultFile);

    /**
     * Invalidate cache (force reload from disk on next read)
     */
    void invalidateCache();
}
