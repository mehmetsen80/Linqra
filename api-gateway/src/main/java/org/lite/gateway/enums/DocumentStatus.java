package org.lite.gateway.enums;

/**
 * Status enum for KnowledgeHubDocument lifecycle
 * Status flow: PENDING_UPLOAD -> UPLOADED -> PARSING -> PROCESSED -> METADATA_EXTRACTION -> EMBEDDING -> AI_READY
 */
public enum DocumentStatus {
    PENDING_UPLOAD,          // Document is created but not yet uploaded
    UPLOADED,                // Document has been uploaded to S3
    PARSING,                 // Document is being parsed
    PROCESSED,               // Document has been parsed and chunked
    METADATA_EXTRACTION,     // Metadata extraction is in progress
    EMBEDDING,               // Embedding generation is in progress
    AI_READY,                // Document is fully processed and ready for AI operations
    FAILED                   // Processing failed at some stage
}

