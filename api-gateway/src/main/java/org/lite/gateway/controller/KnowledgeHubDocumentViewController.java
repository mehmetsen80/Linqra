package org.lite.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.dto.ProcessedDocumentDto;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.ObjectStorageService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentViewController {

        private final KnowledgeHubDocumentService documentService;
        private final ObjectStorageService objectStorageService;
        private final TeamContextService teamContextService;
        private final ObjectMapper objectMapper;
        private final ChunkEncryptionService chunkEncryptionService;

        /**
         * Get document by ID
         */
        @GetMapping("/view/{documentId}")
        @AuditLog(eventType = AuditEventType.DOCUMENT_ACCESSED, action = AuditActionType.READ, resourceType = AuditResourceType.DOCUMENT, resourceIdParam = "documentId", documentIdParam = "documentId", reason = "Document viewed/accessed")
        public Mono<ResponseEntity<KnowledgeHubDocument>> getDocumentById(
                        @PathVariable String documentId,
                        ServerWebExchange exchange) {
                log.info("Getting document: {}", documentId);

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                                                .map(ResponseEntity::ok))
                                .onErrorResume(error -> {
                                        log.error("Error getting document", error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .build());
                                });
        }

        /**
         * Download document file (decrypted if encrypted)
         * Streams the decrypted file directly to the client
         */
        @GetMapping("/view/{documentId}/download")
        public Mono<ResponseEntity<Object>> downloadDocument(
                        @PathVariable String documentId,
                        ServerWebExchange exchange) {
                log.info("Downloading document: {}", documentId);

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                                                .flatMap(document -> {
                                                        // Download encrypted file from S3
                                                        return objectStorageService
                                                                        .downloadFileContent(document.getS3Key())
                                                                        .flatMap(encryptedBytes -> {
                                                                                Mono<byte[]> fileBytesMono;
                                                                                if (Boolean.TRUE
                                                                                                .equals(document.getEncrypted())
                                                                                                && document.getEncryptionKeyVersion() != null) {
                                                                                        fileBytesMono = chunkEncryptionService
                                                                                                        .decryptFile(encryptedBytes,
                                                                                                                        teamId,
                                                                                                                        document.getEncryptionKeyVersion());
                                                                                } else {
                                                                                        // Try to detect legacy
                                                                                        // encrypted
                                                                                        // files (v1 key derived from
                                                                                        // string) - not supported for
                                                                                        // files yet
                                                                                        log.debug("File for document {} is not encrypted (legacy file)",
                                                                                                        documentId);
                                                                                        fileBytesMono = Mono.just(
                                                                                                        encryptedBytes);
                                                                                }

                                                                                return fileBytesMono.map(fileBytes -> {
                                                                                        try {
                                                                                                // Convert bytes to
                                                                                                // DataBuffer for
                                                                                                // streaming
                                                                                                DataBuffer buffer = exchange
                                                                                                                .getResponse()
                                                                                                                .bufferFactory()
                                                                                                                .wrap(fileBytes);

                                                                                                // Set response headers
                                                                                                HttpHeaders headers = new HttpHeaders();
                                                                                                headers.setContentType(
                                                                                                                MediaType.parseMediaType(
                                                                                                                                document.getContentType() != null
                                                                                                                                                ? document.getContentType()
                                                                                                                                                : "application/octet-stream"));
                                                                                                headers.setContentLength(
                                                                                                                fileBytes.length);
                                                                                                // Return file content
                                                                                                return ResponseEntity
                                                                                                                .ok()
                                                                                                                .headers(headers)
                                                                                                                .body((Object) buffer);
                                                                                        } catch (Exception e) {
                                                                                                log.error("Failed to prepare file response for document {}: {}",
                                                                                                                documentId,
                                                                                                                e.getMessage(),
                                                                                                                e);
                                                                                                throw new RuntimeException(
                                                                                                                "Failed to prepare file response",
                                                                                                                e);
                                                                                        }
                                                                                });
                                                                        });
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error downloading document: {}", documentId, error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body((Object) null));
                                });
        }

        /**
         * Generate presigned URL for downloading processed JSON
         */
        @GetMapping("/view/{documentId}/processed/download")
        public Mono<ResponseEntity<Object>> generateProcessedJsonDownloadUrl(
                        @PathVariable String documentId,
                        ServerWebExchange exchange) {
                log.info("Generating download URL for processed JSON of document: {}", documentId);

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                                                .filter(document -> document.getProcessedS3Key() != null
                                                                && !document.getProcessedS3Key().isEmpty())
                                                .switchIfEmpty(
                                                                Mono.error(new RuntimeException(
                                                                                "Processed JSON not available for this document")))
                                                .flatMap(document -> objectStorageService
                                                                .generatePresignedDownloadUrl(
                                                                                document.getProcessedS3Key())
                                                                .map(url -> {
                                                                        String fileName = document.getFileName();
                                                                        String jsonFileName = fileName.endsWith(".json")
                                                                                        ? fileName
                                                                                        : fileName.substring(0, fileName
                                                                                                        .lastIndexOf('.'))
                                                                                                        + "_processed.json";
                                                                        Map<String, String> response = Map.of(
                                                                                        "downloadUrl", url,
                                                                                        "fileName", jsonFileName);
                                                                        return ResponseEntity.<Object>ok(response);
                                                                })))
                                .onErrorResume(error -> {
                                        log.error("Error generating processed JSON download URL", error);
                                        if (error instanceof RuntimeException
                                                        && error.getMessage().contains("not available")) {
                                                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                                .body(Map.of("error", error.getMessage())));
                                        }
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(Map.of("error", "Failed to generate download URL")));
                                });
        }

        /**
         * Get decrypted processed JSON for viewing in the frontend
         */
        @GetMapping("/view/{documentId}/processed")
        public Mono<ResponseEntity<ProcessedDocumentDto>> getProcessedJson(
                        @PathVariable String documentId,
                        ServerWebExchange exchange) {
                log.info("Getting decrypted processed JSON for document: {}", documentId);

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                                                .filter(document -> document.getProcessedS3Key() != null
                                                                && !document.getProcessedS3Key().isEmpty())
                                                .switchIfEmpty(
                                                                Mono.error(new RuntimeException(
                                                                                "Processed JSON not available for this document")))
                                                .flatMap(document -> objectStorageService
                                                                .downloadFileContent(document.getProcessedS3Key())
                                                                .flatMap(bytes -> {
                                                                        try {
                                                                                ProcessedDocumentDto processedDoc = objectMapper
                                                                                                .readValue(
                                                                                                                new String(bytes,
                                                                                                                                StandardCharsets.UTF_8),
                                                                                                                ProcessedDocumentDto.class);
                                                                                // Decrypt sensitive fields in processed
                                                                                // document
                                                                                return decryptProcessedDocumentDto(
                                                                                                processedDoc, teamId)
                                                                                                .thenReturn(processedDoc);
                                                                        } catch (Exception e) {
                                                                                log.error(
                                                                                                "Failed to parse and decrypt processed document JSON for document {}: {}",
                                                                                                documentId,
                                                                                                e.getMessage(), e);
                                                                                return Mono.error(new RuntimeException(
                                                                                                "Failed to parse processed document: "
                                                                                                                + e.getMessage(),
                                                                                                e));
                                                                        }
                                                                })
                                                                .map(ResponseEntity::ok)))
                                .onErrorResume(error -> {
                                        log.error("Error getting processed JSON for document {}: {}", documentId,
                                                        error.getMessage(),
                                                        error);
                                        if (error instanceof RuntimeException
                                                        && error.getMessage().contains("not available")) {
                                                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                                        }
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .build());
                                });
        }

        /**
         * Decrypt sensitive fields in the processed document DTO
         */
        /**
         * Decrypt sensitive fields in the processed document DTO
         */
        private Mono<Void> decryptProcessedDocumentDto(ProcessedDocumentDto processedDoc, String teamId) {
                if (processedDoc == null) {
                        return Mono.empty();
                }

                String keyVersion = processedDoc.getEncryptionKeyVersion();
                if (keyVersion == null || keyVersion.isEmpty()) {
                        keyVersion = "v1"; // Default to v1 for legacy data
                }

                final String finalKeyVersion = keyVersion;
                List<Mono<Void>> decryptionTasks = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

                // Decrypt chunk text
                if (processedDoc.getChunks() != null && !processedDoc.getChunks().isEmpty()) {
                        for (ProcessedDocumentDto.ChunkDto chunk : processedDoc.getChunks()) {
                                if (chunk.getText() != null && !chunk.getText().isEmpty()) {
                                        decryptionTasks.add(chunkEncryptionService.decryptChunkText(
                                                        chunk.getText(),
                                                        teamId,
                                                        finalKeyVersion)
                                                        .doOnNext(chunk::setText)
                                                        .onErrorResume(e -> {
                                                                log.warn(
                                                                                "Failed to decrypt chunk text for team {} with key version {}: {}. Keeping encrypted value.",
                                                                                teamId, finalKeyVersion,
                                                                                e.getMessage());
                                                                return Mono.empty();
                                                        })
                                                        .then());
                                }
                        }
                }

                // Decrypt sensitive metadata fields
                if (processedDoc.getExtractedMetadata() != null) {
                        ProcessedDocumentDto.ExtractedMetadata metadata = processedDoc.getExtractedMetadata();

                        if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getTitle(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setTitle)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getAuthor(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setAuthor)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getSubject(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setSubject)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getKeywords(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setKeywords)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getCreator(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setCreator)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getProducer(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setProducer)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getFormTitle() != null && !metadata.getFormTitle().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getFormTitle(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setFormTitle)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                        if (metadata.getFormNumber() != null && !metadata.getFormNumber().isEmpty()) {
                                decryptionTasks
                                                .add(chunkEncryptionService
                                                                .decryptChunkText(metadata.getFormNumber(), teamId,
                                                                                finalKeyVersion)
                                                                .doOnNext(metadata::setFormNumber)
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then());
                        }
                }

                // Decrypt form field values
                if (processedDoc.getFormFields() != null && !processedDoc.getFormFields().isEmpty()) {
                        for (ProcessedDocumentDto.FormField field : processedDoc.getFormFields()) {
                                if (field.getValue() != null && !field.getValue().isEmpty()) {
                                        decryptionTasks
                                                        .add(chunkEncryptionService
                                                                        .decryptChunkText(field.getValue(), teamId,
                                                                                        finalKeyVersion)
                                                                        .doOnNext(field::setValue)
                                                                        .onErrorResume(e -> {
                                                                                log.debug(
                                                                                                "Failed to decrypt form field value for team {}: {}. Keeping encrypted value.",
                                                                                                teamId, e.getMessage());
                                                                                return Mono.empty();
                                                                        })
                                                                        .then());
                                }
                        }
                }

                return reactor.core.publisher.Flux.merge(decryptionTasks)
                                .then()
                                .doOnSuccess(v -> log.debug(
                                                "Decrypted processed document DTO for team {} with key version {}",
                                                teamId,
                                                finalKeyVersion));
        }
}
