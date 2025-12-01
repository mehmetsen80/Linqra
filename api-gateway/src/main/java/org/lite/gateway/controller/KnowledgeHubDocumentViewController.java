package org.lite.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ProcessedDocumentDto;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.S3Service;
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
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentViewController {
    
    private final KnowledgeHubDocumentService documentService;
    private final S3Service s3Service;
    private final TeamContextService teamContextService;
    private final ObjectMapper objectMapper;
    private final ChunkEncryptionService chunkEncryptionService;
    
    /**
     * Get document by ID
     */
    @GetMapping("/view/{documentId}")
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
    public Mono<ResponseEntity<?>> downloadDocument(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Downloading document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                        .flatMap(document -> {
                            // Download encrypted file from S3
                            return s3Service.downloadFileContent(document.getS3Key())
                                    .flatMap(encryptedBytes -> {
                                        try {
                                            byte[] fileBytes = encryptedBytes;
                                            
                                            // Decrypt if file is encrypted
                                            if (document.getEncrypted() != null && document.getEncrypted() 
                                                    && document.getEncryptionKeyVersion() != null 
                                                    && !document.getEncryptionKeyVersion().isEmpty()) {
                                                log.info("Decrypting file for document: {} (key version: {})", 
                                                        documentId, document.getEncryptionKeyVersion());
                                                fileBytes = chunkEncryptionService.decryptFile(
                                                        encryptedBytes,
                                                        document.getTeamId(),
                                                        document.getEncryptionKeyVersion()
                                                );
                                                log.info("Decrypted file for document {}: {} bytes -> {} bytes", 
                                                        documentId, encryptedBytes.length, fileBytes.length);
                                            } else {
                                                log.debug("File for document {} is not encrypted (legacy file)", documentId);
                                            }
                                            
                                            // Convert bytes to DataBuffer for streaming
                                            DataBuffer buffer = exchange.getResponse()
                                                    .bufferFactory()
                                                    .wrap(fileBytes);
                                            
                                            // Set response headers
                                            HttpHeaders headers = new HttpHeaders();
                                            headers.setContentType(MediaType.parseMediaType(
                                                    document.getContentType() != null ? document.getContentType() : "application/octet-stream"));
                                            headers.setContentLength(fileBytes.length);
                                            headers.setContentDispositionFormData("attachment", document.getFileName());
                                            
                                            return Mono.just(ResponseEntity.ok()
                                                    .headers(headers)
                                                    .body(buffer));
                                            
                                        } catch (Exception e) {
                                            log.error("Failed to decrypt file for document {}: {}", documentId, e.getMessage(), e);
                                            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                                        }
                                    });
                        }))
                .onErrorResume(error -> {
                    log.error("Error downloading document: {}", documentId, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
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
                        .filter(document -> document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty())
                        .switchIfEmpty(Mono.error(new RuntimeException("Processed JSON not available for this document")))
                        .flatMap(document -> s3Service.generatePresignedDownloadUrl(document.getProcessedS3Key())
                                .map(url -> {
                                    String fileName = document.getFileName();
                                    String jsonFileName = fileName.endsWith(".json") 
                                            ? fileName 
                                            : fileName.substring(0, fileName.lastIndexOf('.')) + "_processed.json";
                                    Map<String, String> response = Map.of(
                                            "downloadUrl", url,
                                            "fileName", jsonFileName
                                    );
                                    return ResponseEntity.<Object>ok(response);
                                })))
                .onErrorResume(error -> {
                    log.error("Error generating processed JSON download URL", error);
                    if (error instanceof RuntimeException && error.getMessage().contains("not available")) {
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
                        .filter(document -> document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty())
                        .switchIfEmpty(Mono.error(new RuntimeException("Processed JSON not available for this document")))
                        .flatMap(document -> s3Service.downloadFileContent(document.getProcessedS3Key())
                                .map(bytes -> {
                                    try {
                                        ProcessedDocumentDto processedDoc = objectMapper.readValue(
                                                new String(bytes, StandardCharsets.UTF_8), 
                                                ProcessedDocumentDto.class
                                        );
                                        // Decrypt sensitive fields in processed document
                                        decryptProcessedDocumentDto(processedDoc, teamId);
                                        return processedDoc;
                                    } catch (Exception e) {
                                        log.error("Failed to parse and decrypt processed document JSON for document {}: {}", 
                                                documentId, e.getMessage(), e);
                                        throw new RuntimeException("Failed to parse processed document: " + e.getMessage(), e);
                                    }
                                })
                                .map(ResponseEntity::ok)))
                .onErrorResume(error -> {
                    log.error("Error getting processed JSON for document {}: {}", documentId, error.getMessage(), error);
                    if (error instanceof RuntimeException && error.getMessage().contains("not available")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
    
    /**
     * Decrypt sensitive fields in the processed document DTO
     */
    private void decryptProcessedDocumentDto(ProcessedDocumentDto processedDoc, String teamId) {
        if (processedDoc == null) {
            return;
        }
        
        String keyVersion = processedDoc.getEncryptionKeyVersion();
        if (keyVersion == null || keyVersion.isEmpty()) {
            keyVersion = "v1"; // Default to v1 for legacy data
        }
        
        try {
            // Decrypt chunk text
            if (processedDoc.getChunks() != null && !processedDoc.getChunks().isEmpty()) {
                for (ProcessedDocumentDto.ChunkDto chunk : processedDoc.getChunks()) {
                    if (chunk.getText() != null && !chunk.getText().isEmpty()) {
                        try {
                            String decryptedText = chunkEncryptionService.decryptChunkText(
                                    chunk.getText(), 
                                    teamId, 
                                    keyVersion
                            );
                            chunk.setText(decryptedText);
                        } catch (Exception e) {
                            log.warn("Failed to decrypt chunk text for team {} with key version {}: {}. Keeping encrypted value.", 
                                    teamId, keyVersion, e.getMessage());
                            // Keep encrypted if decryption fails (might be legacy unencrypted data)
                        }
                    }
                }
            }
            
            // Decrypt sensitive metadata fields
            if (processedDoc.getExtractedMetadata() != null) {
                ProcessedDocumentDto.ExtractedMetadata metadata = processedDoc.getExtractedMetadata();
                
                try {
                    if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
                        metadata.setTitle(chunkEncryptionService.decryptChunkText(metadata.getTitle(), teamId, keyVersion));
                    }
                    if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
                        metadata.setAuthor(chunkEncryptionService.decryptChunkText(metadata.getAuthor(), teamId, keyVersion));
                    }
                    if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
                        metadata.setSubject(chunkEncryptionService.decryptChunkText(metadata.getSubject(), teamId, keyVersion));
                    }
                    if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
                        metadata.setKeywords(chunkEncryptionService.decryptChunkText(metadata.getKeywords(), teamId, keyVersion));
                    }
                    if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
                        metadata.setCreator(chunkEncryptionService.decryptChunkText(metadata.getCreator(), teamId, keyVersion));
                    }
                    if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
                        metadata.setProducer(chunkEncryptionService.decryptChunkText(metadata.getProducer(), teamId, keyVersion));
                    }
                    if (metadata.getFormTitle() != null && !metadata.getFormTitle().isEmpty()) {
                        metadata.setFormTitle(chunkEncryptionService.decryptChunkText(metadata.getFormTitle(), teamId, keyVersion));
                    }
                    if (metadata.getFormNumber() != null && !metadata.getFormNumber().isEmpty()) {
                        metadata.setFormNumber(chunkEncryptionService.decryptChunkText(metadata.getFormNumber(), teamId, keyVersion));
                    }
                } catch (Exception e) {
                    log.warn("Failed to decrypt metadata fields for team {} with key version {}: {}. Some fields may remain encrypted.", 
                            teamId, keyVersion, e.getMessage());
                }
            }
            
            // Decrypt form field values
            if (processedDoc.getFormFields() != null && !processedDoc.getFormFields().isEmpty()) {
                for (ProcessedDocumentDto.FormField field : processedDoc.getFormFields()) {
                    if (field.getValue() != null && !field.getValue().isEmpty()) {
                        try {
                            field.setValue(chunkEncryptionService.decryptChunkText(field.getValue(), teamId, keyVersion));
                        } catch (Exception e) {
                            log.debug("Failed to decrypt form field value for team {}: {}. Keeping encrypted value.", 
                                    teamId, e.getMessage());
                        }
                    }
                }
            }
            
            log.debug("Decrypted processed document DTO for team {} with key version {}", teamId, keyVersion);
            
        } catch (Exception e) {
            log.warn("Failed to decrypt some fields in processed document DTO for team {}: {}. Some fields may remain encrypted.", 
                    teamId, e.getMessage());
        }
    }
}

