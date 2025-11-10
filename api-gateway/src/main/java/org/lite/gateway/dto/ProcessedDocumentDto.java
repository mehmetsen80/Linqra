package org.lite.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a fully processed document ready for storage in S3
 * Matches the JSON structure from the example
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessedDocumentDto {
    
    private ProcessingMetadata processingMetadata;
    private SourceDocument sourceDocument;
    private ExtractedMetadata extractedMetadata;
    private ChunkingStrategy chunkingStrategy;
    private List<ChunkDto> chunks;
    private List<FormField> formFields;
    private Statistics statistics;
    private QualityChecks qualityChecks;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessingMetadata {
        private String documentId;
        private String processedAt;
        private String processingVersion;
        private String ingestServiceVersion;
        private Long processingTimeMs;
        private String status;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FormField {
        private String name;
        private String type;
        private String value;
        private List<String> options;
        private Integer pageNumber;
        private Boolean required;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceDocument {
        private String fileName;
        private String s3Key;
        private Long fileSize;
        private String contentType;
        private String uploadedAt;
        private String uploadedBy;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExtractedMetadata {
        private String formNumber;
        private String formTitle;
        private String versionDate;
        private String category;
        private String language;
        private Integer pageCount;
        private List<String> detectedEntities;
        // Tika metadata fields
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private String creator;
        private String producer;
        private String creationDate;
        private String modificationDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChunkingStrategy {
        private String method;
        private Integer maxTokens;
        private Integer overlapTokens;
        private String tokenizer;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChunkDto {
        private String chunkId;
        private Integer chunkIndex;
        private String text;
        private Integer tokenCount;
        private Integer startPosition;
        private Integer endPosition;
        private List<Integer> pageNumbers;
        private Boolean containsTable;
        private String language;
        private Double qualityScore;
        private Boolean metadataOnly;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Statistics {
        private Integer totalChunks;
        private Integer totalTokens;
        private Integer avgTokensPerChunk;
        private Double avgQualityScore;
        private Integer chunksWithLessThan50Tokens;
        private Integer chunksWithTables;
        // Text statistics
        private Integer pageCount;
        private Integer wordCount;
        private Integer characterCount;
        private String language;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QualityChecks {
        private Boolean allChunksValid;
        private List<String> warnings;
        private List<String> errors;
    }
}

