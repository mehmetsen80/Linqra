package org.lite.gateway.service;

import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface KnowledgeHubCollectionService {
    
    Mono<KnowledgeHubCollection> createCollection(String name, String description, List<KnowledgeCategory> categories, String teamId, String createdBy);
    
    Mono<KnowledgeHubCollection> updateCollection(String id, String name, String description, List<KnowledgeCategory> categories, String teamId, String updatedBy);
    
    Mono<Void> deleteCollection(String id, String teamId);
    
    Flux<KnowledgeHubCollection> getCollectionsByTeam(String teamId);
    
    Mono<KnowledgeHubCollection> getCollectionById(String id, String teamId);
    
    Mono<Long> getCollectionCountByTeam(String teamId);
}

