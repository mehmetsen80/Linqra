package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface KnowledgeCollectionRepository extends ReactiveMongoRepository<KnowledgeCollection, String> {
    
    Flux<KnowledgeCollection> findByTeamId(String teamId);
    
    Flux<KnowledgeCollection> findByTeamId(Sort sort, String teamId);
    
    Flux<KnowledgeCollection> findByTeamIdAndCategoriesContaining(String teamId, KnowledgeCategory category);
    
    Mono<KnowledgeCollection> findByTeamIdAndName(String teamId, String name);
    
    Mono<KnowledgeCollection> findByIdAndTeamId(String id, String teamId);
    
    Mono<Long> countByTeamId(String teamId);
}

