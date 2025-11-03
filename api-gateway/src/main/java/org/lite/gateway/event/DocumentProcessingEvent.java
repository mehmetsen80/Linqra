package org.lite.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published when a document needs to be processed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingEvent {
    private String documentId;
    private String teamId;
    private String collectionId;
    private String s3Bucket;
    private String s3Key;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Integer chunkSize;
    private Integer overlapTokens;
    private String chunkStrategy;
}

