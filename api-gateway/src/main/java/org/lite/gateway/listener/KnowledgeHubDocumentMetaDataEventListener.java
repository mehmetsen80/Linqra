package org.lite.gateway.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.event.KnowledgeHubDocumentMetaDataEvent;
import org.lite.gateway.service.KnowledgeHubDocumentMetaDataService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listener for KnowledgeHubDocumentMetaDataEvent
 * Triggers metadata extraction asynchronously when document processing is complete
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeHubDocumentMetaDataEventListener {
    
    private final KnowledgeHubDocumentMetaDataService metadataService;
    
    @Async
    @EventListener
    public void handleDocumentMetaDataEvent(KnowledgeHubDocumentMetaDataEvent event) {
        log.info("Received document metadata extraction event for document: {}, team: {}", 
                event.getDocumentId(), event.getTeamId());
        
        metadataService.extractMetadata(event.getDocumentId(), event.getTeamId())
                .doOnSuccess(metadata -> log.info("Successfully extracted metadata for document: {}", event.getDocumentId()))
                .doOnError(error -> log.error("Failed to extract metadata for document: {}", event.getDocumentId(), error))
                .subscribe();
    }
}

