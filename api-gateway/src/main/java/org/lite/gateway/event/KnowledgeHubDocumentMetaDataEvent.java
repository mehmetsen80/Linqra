package org.lite.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event published when document processing is complete and metadata extraction can be triggered
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeHubDocumentMetaDataEvent {
    private String documentId;
    private String teamId;
    private String collectionId;
    private String processedS3Key;
    private String fileName;
    private String contentType;
}

