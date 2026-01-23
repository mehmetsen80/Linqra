package org.lite.gateway.service;

import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.dto.StorageMetadata;
import org.lite.gateway.dto.StorageObject;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ObjectStorageService {

    /**
     * Generate presigned URL for client-side upload
     */
    Mono<PresignedUploadUrl> generatePresignedUploadUrl(String key, String contentType);

    /**
     * Upload file directly (server-side) - Reactive version
     */
    Mono<Void> uploadFile(String key, Flux<DataBuffer> content, String contentType, long fileSize);

    /**
     * Upload file bytes directly (server-side) - Reactive version
     * Convenient method for uploading encrypted files
     * 
     * @param key                  Key for the file
     * @param fileBytes            File bytes to upload
     * @param contentType          Content type of the file
     * @param encryptionKeyVersion Optional encryption key version (e.g., "v1",
     *                             "v2"). If provided, will be stored in metadata
     */
    Mono<Void> uploadFileBytes(String key, byte[] fileBytes, String contentType, String encryptionKeyVersion);

    /**
     * Upload file bytes directly (server-side) to a specific bucket
     * 
     * @param bucketName           Target bucket name
     * @param key                  Key for the file
     * @param fileBytes            File bytes to upload
     * @param contentType          Content type of the file
     * @param encryptionKeyVersion Optional encryption key version
     */
    Mono<Void> uploadFileBytes(String bucketName, String key, byte[] fileBytes, String contentType,
            String encryptionKeyVersion);

    /**
     * Download file - Reactive version
     * Note: This is a placeholder implementation. For actual file download, use
     * presigned URL
     */
    Mono<Void> downloadFile(String key);

    /**
     * Download file content as byte array
     */
    Mono<byte[]> downloadFileContent(String key);

    /**
     * Check if file exists
     */
    Mono<Boolean> fileExists(String key);

    /**
     * Delete file
     */
    Mono<Void> deleteFile(String key);

    /**
     * List files with prefix
     */
    Mono<List<StorageObject>> listFiles(String prefix);

    /**
     * Get file metadata
     */
    Mono<StorageMetadata> getFileMetadata(String key);

    /**
     * Copy file within storage
     */
    Mono<Void> copyFile(String sourceKey, String destinationKey);

    /**
     * Generate presigned URL for downloading
     */
    Mono<String> generatePresignedDownloadUrl(String key);
}
