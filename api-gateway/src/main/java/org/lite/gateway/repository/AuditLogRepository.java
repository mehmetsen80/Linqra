package org.lite.gateway.repository;

import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit logs with reactive MongoDB operations
 */
@Repository
public interface AuditLogRepository extends ReactiveMongoRepository<AuditLog, String> {
    
    /**
     * Find audit logs by event ID (unique)
     */
    Mono<AuditLog> findByEventId(String eventId);
    
    /**
     * Find audit logs by team ID, ordered by timestamp descending
     */
    Flux<AuditLog> findByTeamIdOrderByTimestampDesc(String teamId);
    
    /**
     * Find audit logs by team ID and timestamp range
     */
    Flux<AuditLog> findByTeamIdAndTimestampBetween(
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by team ID, event type, and timestamp range
     */
    Flux<AuditLog> findByTeamIdAndEventTypeAndTimestampBetween(
            String teamId,
            AuditEventType eventType,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by team ID and result (SUCCESS/FAILED/DENIED)
     */
    Flux<AuditLog> findByTeamIdAndResultAndTimestampBetween(
            String teamId,
            String result,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by user ID and timestamp range
     */
    Flux<AuditLog> findByUserIdAndTimestampBetween(
            String userId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by team ID and user ID
     */
    Flux<AuditLog> findByTeamIdAndUserIdAndTimestampBetween(
            String teamId,
            String userId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by document ID
     */
    Flux<AuditLog> findByDocumentIdAndTimestampBetween(
            String documentId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find audit logs by document ID and team ID (for authorization)
     */
    Flux<AuditLog> findByDocumentIdAndTeamIdAndTimestampBetween(
            String documentId,
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Find logs that need to be archived (older than threshold, not yet archived)
     */
    @Query("{ 'timestamp': { $lt: ?0 }, 'archivedAt': null }")
    Flux<AuditLog> findLogsReadyForArchival(LocalDateTime threshold);
    
    /**
     * Find logs by team that need to be archived
     */
    @Query("{ 'teamId': ?0, 'timestamp': { $lt: ?1 }, 'archivedAt': null }")
    Flux<AuditLog> findLogsReadyForArchivalByTeam(String teamId, LocalDateTime threshold);
    
    /**
     * Count logs ready for archival
     */
    @Query(value = "{ 'timestamp': { $lt: ?0 }, 'archivedAt': null }", count = true)
    Mono<Long> countLogsReadyForArchival(LocalDateTime threshold);
    
    /**
     * Find logs by team ID with pagination support
     * Note: MongoDB reactive repositories don't support Pageable directly in method signatures,
     * but we can use Query with limit and sort
     */
    @Query("{ 'teamId': ?0, 'timestamp': { $gte: ?1, $lte: ?2 } }")
    Flux<AuditLog> findByTeamIdAndTimestampBetween(
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable
    );
    
    /**
     * Find recent audit logs for a team (last N logs)
     */
    Flux<AuditLog> findTop100ByTeamIdOrderByTimestampDesc(String teamId);
    
    /**
     * Find audit logs by multiple event types
     */
    @Query("{ 'teamId': ?0, 'eventType': { $in: ?1 }, 'timestamp': { $gte: ?2, $lte: ?3 } }")
    Flux<AuditLog> findByTeamIdAndEventTypeInAndTimestampBetween(
            String teamId,
            List<AuditEventType> eventTypes,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * Count audit logs by team and time range
     */
    Mono<Long> countByTeamIdAndTimestampBetween(
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}

