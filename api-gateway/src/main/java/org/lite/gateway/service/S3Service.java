package org.lite.gateway.service;

import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.dto.S3FileMetadata;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

public interface S3Service {
    
    /**
     * Generate presigned URL for client-side upload
     */
    Mono<PresignedUploadUrl> generatePresignedUploadUrl(String s3Key, String contentType);
    
    /**
     * Upload file directly (server-side) - Reactive version
     */
    Mono<Void> uploadFile(String s3Key, Flux<DataBuffer> content, String contentType, long fileSize);
    
    /**
     * Upload file bytes directly (server-side) - Reactive version
     * Convenient method for uploading encrypted files
     * 
     * @param s3Key S3 key for the file
     * @param fileBytes File bytes to upload
     * @param contentType Content type of the file
     * @param encryptionKeyVersion Optional encryption key version (e.g., "v1", "v2"). If provided, will be stored in S3 metadata
     */
    Mono<Void> uploadFileBytes(String s3Key, byte[] fileBytes, String contentType, String encryptionKeyVersion);
    
    /**
     * Download file from S3 - Reactive version
     * Note: This is a placeholder implementation. For actual file download, use presigned URL
     */
    Mono<Void> downloadFile(String s3Key);
    
    /**
     * Download file content as byte array
     */
    Mono<byte[]> downloadFileContent(String s3Key);
    
    /**
     * Check if file exists
     */
    Mono<Boolean> fileExists(String s3Key);
    
    /**
     * Delete file
     */
    Mono<Void> deleteFile(String s3Key);
    
    /**
     * List files with prefix
     */
    Mono<List<S3Object>> listFiles(String prefix);
    
    /**
     * Get file metadata
     */
    Mono<S3FileMetadata> getFileMetadata(String s3Key);
    
    /**
     * Copy file within S3
     */
    Mono<Void> copyFile(String sourceKey, String destinationKey);
    
    /**
     * Generate presigned URL for downloading
     */
    Mono<String> generatePresignedDownloadUrl(String s3Key);
}

