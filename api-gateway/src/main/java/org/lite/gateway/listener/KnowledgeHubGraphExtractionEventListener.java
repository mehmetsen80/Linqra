package org.lite.gateway.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.event.KnowledgeHubDocumentMetaDataEvent;
import org.lite.gateway.service.KnowledgeHubGraphEntityExtractionService;
import org.lite.gateway.service.KnowledgeHubGraphRelationshipExtractionService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Listener for Knowledge Graph extraction events
 * Triggers entity and relationship extraction asynchronously after document metadata extraction
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeHubGraphExtractionEventListener {
    
    private final KnowledgeHubGraphEntityExtractionService entityExtractionService;
    private final KnowledgeHubGraphRelationshipExtractionService relationshipExtractionService;
    
    @Async
    @EventListener
    public void handleDocumentMetaDataEvent(KnowledgeHubDocumentMetaDataEvent event) {
        String documentId = event.getDocumentId();
        String teamId = event.getTeamId();
        
        log.info("Received document metadata event for document: {}, team: {}. Triggering entity extraction.", 
                documentId, teamId);
        
        // Extract entities first, then relationships
        entityExtractionService.extractEntitiesFromDocument(documentId, teamId)
                .doOnSuccess(entityCount -> 
                        log.info("Successfully extracted {} entities for document: {}", entityCount, documentId))
                .flatMap(entityCount -> 
                        // After entities are extracted, extract relationships
                        relationshipExtractionService.extractRelationshipsFromDocument(documentId, teamId)
                                .doOnSuccess(relationshipCount -> 
                                        log.info("Successfully extracted {} relationships for document: {}", 
                                                relationshipCount, documentId))
                                .doOnError(error -> 
                                        log.error("Failed to extract relationships for document: {}", documentId, error))
                                .then(Mono.just(entityCount)))
                .doOnError(error -> log.error("Failed to extract entities for document: {}", documentId, error))
                .subscribe();
    }
}

