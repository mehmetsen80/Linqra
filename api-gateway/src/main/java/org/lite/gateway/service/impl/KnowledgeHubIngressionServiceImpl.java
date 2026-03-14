package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeHubIngressionServiceImpl implements KnowledgeHubIngressionService {

        private final KnowledgeHubDocumentService documentService;
        private final KnowledgeHubCollectionService collectionService;
        private final ObjectStorageService storageService;
        private final WebClient.Builder webClientBuilder;
        private final KnowledgeHubDocumentRepository documentRepository;

        @Override
        public Mono<KnowledgeHubDocument> ingressFromUrl(String url, String fileName, String collectionId,
                        String teamId,
                        String contentType) {
                log.info("Ingressing resource from URL: {} to collection: {} for team: {}", url, collectionId, teamId);

                return collectionService.getCollectionById(collectionId, teamId)
                                .switchIfEmpty(Mono
                                                .error(new RuntimeException("Collection " + collectionId
                                                                + " not found for team " + teamId)))
                                .flatMap(collection -> documentRepository
                                                .findByCollectionIdAndFileName(collectionId, fileName)
                                                .flatMap(existingDoc -> {
                                                        log.info("Document with name {} already exists in collection {}. Skipping ingestion.",
                                                                        fileName, collectionId);
                                                        return Mono.just(existingDoc);
                                                })
                                                .switchIfEmpty(webClientBuilder.build().get()
                                                                .uri(url)
                                                                .retrieve()
                                                                .bodyToMono(byte[].class)
                                                                .flatMap(bytes -> {
                                                                        UploadInitiateRequest uploadRequest = new UploadInitiateRequest();
                                                                        uploadRequest.setFileName(fileName);
                                                                        uploadRequest
                                                                                        .setContentType(contentType != null
                                                                                                        ? contentType
                                                                                                        : "application/octet-stream");
                                                                        uploadRequest.setFileSize((long) bytes.length);
                                                                        uploadRequest.setCollectionId(collectionId);

                                                                        return documentService.initiateDocumentUpload(
                                                                                        uploadRequest, teamId)
                                                                                        .flatMap(initiation -> {
                                                                                                String documentId = initiation
                                                                                                                .document()
                                                                                                                .getDocumentId();
                                                                                                String s3Key = initiation
                                                                                                                .document()
                                                                                                                .getS3Key();

                                                                                                return storageService
                                                                                                                .uploadFileBytes(
                                                                                                                                s3Key,
                                                                                                                                bytes,
                                                                                                                                uploadRequest.getContentType(),
                                                                                                                                null)
                                                                                                                .then(documentService
                                                                                                                                .completeUpload(documentId,
                                                                                                                                                s3Key))
                                                                                                                .doOnSuccess(doc -> log
                                                                                                                                .info(
                                                                                                                                                "Resource from {} indexed as document {} in collection {}",
                                                                                                                                                url,
                                                                                                                                                documentId,
                                                                                                                                                collectionId));
                                                                                        });
                                                                })));
        }
}
