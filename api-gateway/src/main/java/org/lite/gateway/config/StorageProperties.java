package org.lite.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "linqra.storage")
@Data
public class StorageProperties {

    private String type = "s3"; // s3 or minio
    private String endpoint; // Used for MinIO
    private String publicEndpoint; // Public URL for client-side access (e.g. gateway)
    private String accessKey;
    private String secretKey;

    private String bucketName = "linqra-knowledge-hub";
    private String backupBucketName = "backup-linqra-knowledge-hub";
    private String auditBucketName = "linqra-audit";
    private String auditBackupBucketName = "backup-linqra-audit";
    private String backupBucketRegion = "us-east-1";

    private String rawPrefix = "raw";
    private String processedPrefix = "processed";
    private Duration presignedUrlExpiration = Duration.ofMinutes(15);
    private Long maxFileSize = 52428800L; // 50MB default

    /**
     * Build S3 key pattern:
     * {prefix}/{teamId}/{collectionId}/{documentId}_{fileName}
     */
    public String buildRawKey(String teamId, String collectionId,
            String documentId, String fileName) {
        return String.format("%s/%s/%s/%s_%s",
                rawPrefix, teamId, collectionId, documentId, fileName);
    }

    private Redis redis = new Redis();

    @Data
    public static class Redis {
        private String documentProcessingChannel = "document-processing-queue";
    }

    /**
     * Build processed S3 key pattern:
     * {prefix}/{teamId}/{collectionId}/{documentId}.json
     */
    public String buildProcessedKey(String teamId, String collectionId,
            String documentId) {
        return String.format("%s/%s/%s/%s.json",
                processedPrefix, teamId, collectionId, documentId);
    }
}
