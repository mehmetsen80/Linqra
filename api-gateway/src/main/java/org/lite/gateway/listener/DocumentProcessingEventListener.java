package org.lite.gateway.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.event.DocumentProcessingEvent;
import org.lite.gateway.service.DocumentProcessingService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listener for DocumentProcessingEvent
 * Processes documents asynchronously when they are uploaded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingEventListener {
    
    private final DocumentProcessingService documentProcessingService;
    
    @Async
    @EventListener
    public void handleDocumentProcessingEvent(DocumentProcessingEvent event) {
        log.info("Received document processing event for document: {}, team: {}", 
                event.getDocumentId(), event.getTeamId());
        
        documentProcessingService.processDocument(event.getDocumentId(), event.getTeamId())
                .doOnSuccess(v -> log.info("Successfully processed document: {}", event.getDocumentId()))
                .doOnError(error -> log.error("Failed to process document: {}", event.getDocumentId(), error))
                .subscribe();
    }
}

