package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.StorageProperties;
import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.dto.StorageMetadata;
import org.lite.gateway.dto.StorageObject;
import org.lite.gateway.exception.StorageException;
import org.lite.gateway.service.ObjectStorageService;
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
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObjectStorageServiceImpl implements ObjectStorageService {

    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    @Override
    public Mono<PresignedUploadUrl> generatePresignedUploadUrl(String key, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                log.info("Generating presigned URL for key: {}", key);

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(storageProperties.getBucketName())
                        .key(key)
                        // Content-Type removed from signature to allow frontend flexibility
                        // .contentType(contentType)
                        .build();

                PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                        .signatureDuration(storageProperties.getPresignedUrlExpiration())
                        .putObjectRequest(putObjectRequest)
                        .build();

                PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
                log.info("Full Presigned URL: {}", presignedRequest.url().toString());

                // Replace internal endpoint (e.g. localhost:9000) with public gateway endpoint
                // (localhost:7777)
                // This ensures the Host header matches what MinIO expects (localhost:9000) via
                // the gateway's proxying
                String internalUrl = presignedRequest.url().toString();
                String publicUrl = internalUrl.replace("http://localhost:9000", "https://localhost:7777");

                log.info("Internal Presigned URL: {}", internalUrl);
                log.info("Public Presigned URL: {}", publicUrl);

                return PresignedUploadUrl.builder()
                        .uploadUrl(publicUrl)
                        .s3Key(key) // Retain s3Key field name for compatibility, populate with generic key
                        .expiresAt(Instant.now().plus(storageProperties.getPresignedUrlExpiration()))
                        .requiredHeaders(Map.of()) // No longer enforcing Content-Type header
                        .build();

            } catch (Exception e) {
                log.error("Failed to generate presigned URL for key: {}", key, e);
                throw new StorageException("Failed to generate upload URL", e);
            }
        });
    }

    @Override
    public Mono<Void> uploadFile(String key, Flux<DataBuffer> content, String contentType, long fileSize) {
        return DataBufferUtils.join(content)
                .flatMap(dataBuffer -> {
                    try {
                        log.info("Uploading file to storage: {}", key);

                        ByteBuffer byteBuffer = dataBuffer.readableByteBuffers().next();

                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("uploaded-at", Instant.now().toString());
                        metadata.put("upload-method", "server-side");

                        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                                .bucket(storageProperties.getBucketName())
                                .key(key)
                                .contentType(contentType)
                                .contentLength(fileSize)
                                .metadata(metadata)
                                // Only apply SSE if configured or if AWS (MinIO might support it differently)
                                // .serverSideEncryption(ServerSideEncryption.AES256)
                                .build();

                        CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                                putObjectRequest,
                                AsyncRequestBody.fromByteBuffer(byteBuffer));

                        return Mono.fromFuture(future)
                                .doOnSuccess(response -> log.info("Successfully uploaded file: {}", key))
                                .then();

                    } catch (Exception e) {
                        log.error("Failed to upload file: {}", key, e);
                        return Mono.error(new StorageException("Failed to upload file", e));
                    }
                });
    }

    @Override
    public Mono<Void> uploadFileBytes(String key, byte[] fileBytes, String contentType, String encryptionKeyVersion) {
        return uploadFileBytes(storageProperties.getBucketName(), key, fileBytes, contentType, encryptionKeyVersion);
    }

    @Override
    public Mono<Void> uploadFileBytes(String bucketName, String key, byte[] fileBytes, String contentType,
            String encryptionKeyVersion) {
        return Mono.fromCallable(() -> {
            try {
                log.info("Uploading file bytes to Bucket: {} Key: {} ({} bytes)", bucketName, key, fileBytes.length);

                Map<String, String> metadata = new HashMap<>();
                metadata.put("uploaded-at", Instant.now().toString());
                metadata.put("upload-method", "server-side-encrypted");

                if (encryptionKeyVersion != null && !encryptionKeyVersion.isEmpty()) {
                    metadata.put("encryption-key-version", encryptionKeyVersion);
                }

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) fileBytes.length)
                        .metadata(metadata)
                        .build();

                ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
                CompletableFuture<PutObjectResponse> future = s3AsyncClient.putObject(
                        putObjectRequest,
                        AsyncRequestBody.fromByteBuffer(byteBuffer));

                return future;
            } catch (Exception e) {
                log.error("Failed to upload file bytes: {}", key, e);
                throw new StorageException("Failed to upload file bytes", e);
            }
        })
                .flatMap(future -> Mono.fromFuture(future))
                .doOnSuccess(response -> log.info("Successfully uploaded file bytes: {}", key))
                .then();
    }

    @Override
    public Mono<Void> downloadFile(String key) {
        log.info("Download requested for key: {} - Use generatePresignedDownloadUrl instead", key);
        return Mono.empty();
    }

    @Override
    public Mono<byte[]> downloadFileContent(String key) {
        return Mono.fromCallable(() -> {
            // log.info("Downloading file content: {}", key);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            return s3AsyncClient.getObject(getObjectRequest,
                    AsyncResponseTransformer.toBytes());
        })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> response.asByteArray())
                .doOnError(error -> log.error("Failed to download file: {}", key, error));
    }

    @Override
    public Mono<Boolean> fileExists(String key) {
        return Mono.fromCallable(() -> {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            return s3AsyncClient.headObject(headObjectRequest);
        })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> true)
                .onErrorReturn(NoSuchKeyException.class, false)
                .onErrorResume(e -> {
                    // 404 might come as generic exception depending on client
                    if (e.getMessage().contains("404"))
                        return Mono.just(false);
                    return Mono.error(e);
                })
                .onErrorReturn(false);
    }

    @Override
    public Mono<Void> deleteFile(String key) {
        return Mono.fromCallable(() -> {
            log.info("Deleting file: {}", key);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            return s3AsyncClient.deleteObject(deleteObjectRequest);
        })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .doOnSuccess(response -> log.info("Successfully deleted file: {}", key))
                .then()
                .onErrorMap(e -> new StorageException("Failed to delete file", e));
    }

    @Override
    public Mono<List<StorageObject>> listFiles(String prefix) {
        return Mono.fromCallable(() -> {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(storageProperties.getBucketName())
                    .prefix(prefix)
                    .build();

            return s3AsyncClient.listObjectsV2(listRequest);
        })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> response.contents().stream()
                        .map(s3Object -> StorageObject.builder()
                                .key(s3Object.key())
                                .size(s3Object.size())
                                .lastModified(s3Object.lastModified())
                                .eTag(s3Object.eTag())
                                .storageClass(s3Object.storageClassAsString())
                                .owner(s3Object.owner() != null ? s3Object.owner().displayName() : null)
                                .build())
                        .collect(Collectors.toList()))
                .onErrorMap(e -> new StorageException("Failed to list files", e));
    }

    @Override
    public Mono<StorageMetadata> getFileMetadata(String key) {
        return Mono.fromCallable(() -> {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            return s3AsyncClient.headObject(headObjectRequest);
        })
                .flatMap(completableFuture -> Mono.fromFuture(completableFuture))
                .map(response -> StorageMetadata.builder()
                        .key(key)
                        .contentType(response.contentType())
                        .contentLength(response.contentLength())
                        .lastModified(response.lastModified())
                        .eTag(response.eTag())
                        .metadata(response.metadata())
                        .build())
                .onErrorMap(NoSuchKeyException.class,
                        e -> new StorageException("File not found: " + key, e))
                .onErrorMap(e -> !(e instanceof StorageException),
                        e -> new StorageException("Failed to get file metadata", e));
    }

    @Override
    public Mono<Void> copyFile(String sourceKey, String destinationKey) {
        return Mono.fromCallable(() -> {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(storageProperties.getBucketName())
                    .sourceKey(sourceKey)
                    .destinationBucket(storageProperties.getBucketName())
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
    public Mono<String> generatePresignedDownloadUrl(String key) {
        return Mono.fromCallable(() -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        })
                .onErrorMap(e -> new StorageException("Failed to generate download URL", e));
    }
}
