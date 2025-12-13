package org.lite.gateway.repository;

import org.lite.gateway.entity.GraphExtractionJob;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GraphExtractionJobRepository extends ReactiveMongoRepository<GraphExtractionJob, String> {
    
    Mono<GraphExtractionJob> findByJobId(String jobId);
    
    Flux<GraphExtractionJob> findByDocumentIdAndTeamId(String documentId, String teamId);
    
    Flux<GraphExtractionJob> findByTeamIdAndStatus(String teamId, String status);
    
    Flux<GraphExtractionJob> findByDocumentIdAndTeamIdAndStatus(String documentId, String teamId, String status);
    
    Mono<GraphExtractionJob> findFirstByDocumentIdAndTeamIdAndStatusOrderByCreatedAtDesc(
            String documentId, String teamId, String status);
}

