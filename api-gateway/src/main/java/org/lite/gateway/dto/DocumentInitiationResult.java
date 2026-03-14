package org.lite.gateway.dto;

import org.lite.gateway.entity.KnowledgeHubDocument;

public record DocumentInitiationResult(KnowledgeHubDocument document, PresignedUploadUrl presignedUrl) {
}
