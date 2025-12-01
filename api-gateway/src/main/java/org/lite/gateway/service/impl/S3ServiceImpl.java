package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.dto.S3FileMetadata;
import org.lite.gateway.exception.StorageException;
import org.lite.gateway.service.S3Service;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements S3Service {
    
    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    
    @Override
    public Mono<PresignedUploadUrl> generatePresignedUploadUrl(String s3Key, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                log.info("Generating presigned URL for key: {}", s3Key);
                
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(s3Key)
                        .contentType(contentType)
                        // Note: Don't add metadata here as it becomes a signed header
                        // If you need metadata, the client must include x-amz-meta-* headers
                        .build();
                
                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                        .signatureDuration(s3Properties.getPresignedUrlExpiration())
                        .putObjectRequest(putObjectRequest)
                        .build();
                
                PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
                
                return PresignedUploadUrl.builder()
                        .uploadUrl(presignedRequest.url().toString())
                        .s3Key(s3Key)
                        .expiresAt(Instant.now().plus(s3Properties.getPresignedUrlExpiration()))
                        .requiredHeaders(Map.of(
                            "Content-Type", contentType
                        ))
                        .build();
                        
            } catch (Exception e) {
                log.error("Failed to generate presigned URL for key: {}", s3Key, e);
                throw new StorageException("Failed to generate upload URL", e);
            }
        });
    }
    
    @Override
    public Mono<Void> uploadFile(String s3Key, Flux<DataBuffer> content, String contentType, long fileSize) {
        return DataBufferUtils.join(content)
                .flatMap(dataBuffer -> {
                    try {
                        log.info("Uploading file to S3: {}", s3Key);
                        
                        // Use readableByteBuffers() which is the recommended approach in Spring 6.0+
                        ByteBuffer byteBuffer = dataBuffer.readableByteBuffers().next();
                        
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("uploaded-at", Instant.now().toString());
                        metadata.put("upload-method", "server-side");
                        
                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(s3Properties.getBucketName())
                                .key(s3Key)
                                .contentType(contentType)
                                .contentLength(fileSize)
                                .metadata(metadata)
                                .serverSideEncryption(ServerSideEncryption.AES256)
                                .build();
                        
                        CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                                putObjectRequest,
                                AsyncRequestBody.fromByteBuffer(byteBuffer)
                        );
                        
                        return Mono.fromFuture(future)
                                .doOnSuccess(response -> log.info("Successfully uploaded file to S3: {}", s3Key))
                                .then();
                        
                    } catch (Exception e) {
                        log.error("Failed to upload file to S3: {}", s3Key, e);
                        return Mono.error(new StorageException("Failed to upload file to S3", e));
                    }
                });
    }
    
    @Override
    public Mono<Void> uploadFileBytes(String s3Key, byte[] fileBytes, String contentType, String encryptionKeyVersion) {
        return Mono.fromCallable(() -> {
            try {
                log.info("Uploading file bytes to S3: {} ({} bytes)", s3Key, fileBytes.length);
                
                Map<String, String> metadata = new HashMap<>();
                metadata.put("uploaded-at", Instant.now().toString());
                metadata.put("upload-method", "server-side-encrypted");
                
                // Store encryption key version in S3 metadata for recovery/audit purposes
                if (encryptionKeyVersion != null && !encryptionKeyVersion.isEmpty()) {
                    metadata.put("encryption-key-version", encryptionKeyVersion);
                    log.debug("Storing encryption key version {} in S3 metadata for key: {}", encryptionKeyVersion, s3Key);
                }
                
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(s3Properties.getBucketName())
                        .key(s3Key)
                        .contentType(contentType)
                        .contentLength((long) fileBytes.length)
                        .metadata(metadata)
                        .serverSideEncryption(ServerSideEncryption.AES256) // Additional layer of protection
                        .build();
                
                ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
                CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                        putObjectRequest,
                        AsyncRequestBody.fromByteBuffer(byteBuffer)
                );
                
                return future;
            } catch (Exception e) {
                log.error("Failed to upload file bytes to S3: {}", s3Key, e);
                throw new StorageException("Failed to upload file bytes to S3", e);
            }
        })
        .flatMap(future -> Mono.fromFuture(future))
        .doOnSuccess(response -> log.info("Successfully uploaded file bytes to S3: {}", s3Key))
        .then();
    }
    
    @Override
    public Mono<Void> downloadFile(String s3Key) {
        // Placeholder implementation - use generatePresignedDownloadUrl for actual downloads
        log.info("Download requested for S3 key: {} - Use generatePresignedDownloadUrl instead", s3Key);
        return Mono.empty();
    }
    
    @Override
    public Mono<byte[]> downloadFileContent(String s3Key) {
        return Mono.fromCallable(() -> {
                    log.info("Downloading file content from S3: {}", s3Key);
                    log.info("  - Bucket: {}", s3Properties.getBucketName());
                    log.info("  - S3 Key: {}", s3Key);
                    String bucketName = s3Properties.getBucketName();
                    
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .build();
                    
                    log.info("  - GetObjectRequest created, calling getObject...");
                    // Use AsyncResponseTransformer.toBytes() to get the entire file as bytes
                    return s3AsyncClient.getObject(getObjectRequest, 
                            AsyncResponseTransformer.toBytes());
                })
                .flatMap(completableFuture -> {
                    log.info("  - Waiting for CompletableFuture to complete...");
                    return Mono.fromFuture(completableFuture);
                })
                .map(response -> {
                    byte[] bytes = response.asByteArray();
                    log.info("✅ Successfully downloaded {} bytes from S3 key: {}", bytes.length, s3Key);
                    return bytes;
                })
                .doOnError(error -> {
                    log.error("❌ Failed to download file from S3: {}", s3Key);
                    log.error("  - Error type: {}", error.getClass().getName());
                    log.error("  - Error message: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("  - Cause: {}", error.getCause().getClass().getName());
                        log.error("  - Cause message: {}", error.getCause().getMessage());
                    }
                    log.error("  - Full stack trace:", error);
                });
    }
    
    @Override
    public Mono<Boolean> fileExists(String s3Key) {
        return Mono.fromCallable(() -> {
                    String bucketName = s3Properties.getBucketName();
                    log.info("Checking if file exists - Bucket: {}, Key: {}", bucketName, s3Key);
                    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .build();
                    
                    return s3AsyncClient.headObject(headObjectRequest);
                })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> {
                    log.info("File exists in S3");
                    return true;
                })
                .doOnError(error -> log.error("File does not exist or error occurred: {}", error.getMessage()))
                .onErrorReturn(false);
    }
    
    @Override
    public Mono<Void> deleteFile(String s3Key) {
        return Mono.fromCallable(() -> {
                    log.info("Deleting file from S3: {}", s3Key);
                    
                    DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                            .bucket(s3Properties.getBucketName())
                            .key(s3Key)
                            .build();
                    
                    return s3AsyncClient.deleteObject(deleteObjectRequest);
                })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .doOnSuccess(response -> log.info("Successfully deleted file from S3: {}", s3Key))
                .then()
                .onErrorMap(e -> new StorageException("Failed to delete file from S3", e));
    }
    
    @Override
    public Mono<List<S3Object>> listFiles(String prefix) {
        return Mono.fromCallable(() -> {
                    log.info("Listing files with prefix: {}", prefix);
                    
                    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                            .bucket(s3Properties.getBucketName())
                            .prefix(prefix)
                            .build();
                    
                    return s3AsyncClient.listObjectsV2(listRequest);
                })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> response.contents())
                .onErrorMap(e -> new StorageException("Failed to list files", e));
    }
    
    @Override
    public Mono<S3FileMetadata> getFileMetadata(String s3Key) {
        return Mono.fromCallable(() -> {
                    log.info("Getting file metadata: {}", s3Key);
                    
                    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                            .bucket(s3Properties.getBucketName())
                            .key(s3Key)
                            .build();
                    
                    return s3AsyncClient.headObject(headObjectRequest);
                })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> S3FileMetadata.builder()
                        .s3Key(s3Key)
                        .contentType(response.contentType())
                        .contentLength(response.contentLength())
                        .lastModified(response.lastModified())
                        .eTag(response.eTag())
                        .metadata(response.metadata())
                        .build())
                .onErrorMap(NoSuchKeyException.class, 
                    e -> new StorageException("File not found: " + s3Key, e))
                .onErrorMap(e -> !(e instanceof StorageException), 
                    e -> new StorageException("Failed to get file metadata", e));
    }
    
    @Override
    public Mono<Void> copyFile(String sourceKey, String destinationKey) {
        return Mono.fromCallable(() -> {
                    log.info("Copying file from {} to {}", sourceKey, destinationKey);
                    
                    CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                            .sourceBucket(s3Properties.getBucketName())
                            .sourceKey(sourceKey)
                            .destinationBucket(s3Properties.getBucketName())
                            .destinationKey(destinationKey)
                            .build();
                    
                    return s3AsyncClient.copyObject(copyRequest);
                })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .doOnSuccess(response -> log.info("Successfully copied file"))
                .then()
                .onErrorMap(e -> new StorageException("Failed to copy file", e));
    }
    
    @Override
    public Mono<String> generatePresignedDownloadUrl(String s3Key) {
        return Mono.fromCallable(() -> {
                    log.info("Generating presigned download URL for key: {}", s3Key);
                    
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(s3Properties.getBucketName())
                            .key(s3Key)
                            .build();
                    
                    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(15))
                            .getObjectRequest(getObjectRequest)
                            .build();
                    
                    PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
                    
                    return presignedRequest.url().toString();
                })
                .onErrorMap(e -> new StorageException("Failed to generate download URL", e));
    }
}

