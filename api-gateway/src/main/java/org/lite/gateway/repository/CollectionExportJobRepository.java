package org.lite.gateway.repository;

import org.lite.gateway.entity.CollectionExportJob;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CollectionExportJobRepository extends ReactiveMongoRepository<CollectionExportJob, String> {
    
    Mono<CollectionExportJob> findByJobId(String jobId);
    
    Flux<CollectionExportJob> findByTeamId(String teamId);
    
    Flux<CollectionExportJob> findByTeamIdOrderByCreatedAtDesc(String teamId);
    
    Flux<CollectionExportJob> findByExportedByOrderByCreatedAtDesc(String exportedBy);
    
    Flux<CollectionExportJob> findByTeamIdAndStatus(String teamId, String status);
}

