package org.lite.gateway.service;

import org.lite.gateway.entity.KnowledgeCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface KnowledgeCollectionService {
    
    Mono<KnowledgeCollection> createCollection(String name, String description, List<KnowledgeCategory> categories, String teamId, String createdBy);
    
    Mono<KnowledgeCollection> updateCollection(String id, String name, String description, List<KnowledgeCategory> categories, String teamId, String updatedBy);
    
    Mono<Void> deleteCollection(String id, String teamId);
    
    Flux<KnowledgeCollection> getCollectionsByTeam(String teamId);
    
    Mono<KnowledgeCollection> getCollectionById(String id, String teamId);
    
    Mono<Long> getCollectionCountByTeam(String teamId);
}

