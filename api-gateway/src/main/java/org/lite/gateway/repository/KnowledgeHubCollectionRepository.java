package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface KnowledgeHubCollectionRepository extends ReactiveMongoRepository<KnowledgeHubCollection, String> {
    
    Flux<KnowledgeHubCollection> findByTeamId(String teamId);
    
    Flux<KnowledgeHubCollection> findByTeamId(Sort sort, String teamId);
    
    Flux<KnowledgeHubCollection> findByTeamIdAndCategoriesContaining(String teamId, KnowledgeCategory category);
    
    Mono<KnowledgeHubCollection> findByTeamIdAndName(String teamId, String name);
    
    Mono<KnowledgeHubCollection> findByIdAndTeamId(String id, String teamId);
    
    Mono<Long> countByTeamId(String teamId);
}
