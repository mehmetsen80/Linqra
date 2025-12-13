package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.enums.DocumentStatus;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentCleanupService {

    private final KnowledgeHubDocumentRepository documentRepository;
    private final KnowledgeHubDocumentService documentService;
    private static final int TRASH_RETENTION_DAYS = 30;

    /**
     * Daily cleanup of soft-deleted documents older than 30 days
     */
    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    public void cleanupTrashedDocuments() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        log.info("Starting Trash Can cleanup for documents deleted before {}", threshold);

        documentRepository.findByStatusAndDeletedAtBefore(DocumentStatus.DELETED, threshold)
                .flatMap(doc -> {
                    log.info("Permanently deleting expired trashed document: {} (Deleted at: {})",
                            doc.getDocumentId(), doc.getDeletedAt());

                    return documentService.hardDeleteDocument(doc.getDocumentId(), doc.getTeamId())
                            .then(Mono.just(doc)) // Continue stream
                            .onErrorResume(e -> {
                                log.error("Failed to cleanup document {}: {}", doc.getDocumentId(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .count()
                .subscribe(
                        count -> log.info("Trash Can cleanup completed. Permanently deleted {} documents.", count),
                        error -> log.error("Trash Can cleanup failed: {}", error.getMessage(), error));
    }
}
