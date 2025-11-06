package org.lite.gateway.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.event.KnowledgeHubDocumentProcessingEvent;
import org.lite.gateway.service.KnowledgeHubDocumentProcessingService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listener for KnowledgeHubDocumentProcessingEvent
 * Processes documents asynchronously when they are uploaded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeHubDocumentProcessingEventListener {
    
    private final KnowledgeHubDocumentProcessingService documentProcessingService;
    
    @Async
    @EventListener
    public void handleDocumentProcessingEvent(KnowledgeHubDocumentProcessingEvent event) {
        log.info("Received document processing event for document: {}, team: {}", 
                event.getDocumentId(), event.getTeamId());
        
        documentProcessingService.processDocument(event.getDocumentId(), event.getTeamId())
                .doOnSuccess(v -> log.info("Successfully processed document: {}", event.getDocumentId()))
                .doOnError(error -> log.error("Failed to process document: {}", event.getDocumentId(), error))
                .subscribe();
    }
}

