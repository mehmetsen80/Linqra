package org.lite.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "linqra.knowledgehub.s3")
@Data
public class KnowledgeHubS3Properties {

    private String bucketName = "linqra-knowledge-hub";
    private String backupBucketName = "backup-linqra-knowledge-hub";
    private String backupBucketRegion = "us-east-1";
    private String rawPrefix = "raw";
    private String processedPrefix = "processed";
    private Duration presignedUrlExpiration = Duration.ofMinutes(15);
    private Long maxFileSize = 52428800L; // 50MB default

    /**
     * Build S3 key pattern:
     * {prefix}/{teamId}/{collectionId}/{documentId}_{fileName}
     * 
     * @param teamId       Team ID
     * @param collectionId Collection ID (e.g., knowledge base name)
     * @param documentId   Unique document ID
     * @param fileName     Original file name
     * @return S3 key
     */
    public String buildRawKey(String teamId, String collectionId,
            String documentId, String fileName) {
        return String.format("%s/%s/%s/%s_%s",
                rawPrefix, teamId, collectionId, documentId, fileName);
    }

    /**
     * Build processed S3 key pattern:
     * {prefix}/{teamId}/{collectionId}/{documentId}.json
     * 
     * @param teamId       Team ID
     * @param collectionId Collection ID
     * @param documentId   Document ID
     * @return S3 key for processed data
     */
    public String buildProcessedKey(String teamId, String collectionId,
            String documentId) {
        return String.format("%s/%s/%s/%s.json",
                processedPrefix, teamId, collectionId, documentId);
    }
}
