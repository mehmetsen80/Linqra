package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "knowledge_hub_document_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        // Query versions by document ID and version number
        @CompoundIndex(name = "doc_version_idx", def = "{'documentId': 1, 'versionNumber': 1}", unique = true),
        // Query by team for access control
        @CompoundIndex(name = "team_doc_idx", def = "{'teamId': 1, 'documentId': 1}")
})
public class KnowledgeHubDocumentVersion {

    @Id
    private String id;

    @Indexed
    private String documentId;

    @Indexed
    private String teamId;

    private Integer versionNumber;

    private String s3Key; // S3 key for this specific version
    private Long fileSize;

    // Encryption metadata for this version
    @Builder.Default
    private Boolean encrypted = false;
    private String encryptionKeyVersion;

    private String summary; // Optional: "AI Edit", "User Edit", "Manual Restore"

    @CreatedDate
    private LocalDateTime createdAt;

    private String createdBy; // User ID who made the change
}
