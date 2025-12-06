package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AuditLogPageResponse;
import org.lite.gateway.dto.AuditLogQueryRequest;
import org.lite.gateway.dto.AuditLogResponse;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.repository.AuditLogRepository;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    
    private static final String SYSTEM_USER = UserContextService.SYSTEM_USER;
    
    private final AuditLogRepository auditLogRepository;
    private final UserContextService userContextService;
    private final TeamContextService teamContextService;
    
    @Override
    public Mono<Void> logEvent(
            ServerWebExchange exchange,
            AuditEventType eventType,
            String action,
            String resourceType,
            String resourceId,
            String result,
            AuditLog.AuditMetadata metadata
    ) {
        // Extract context from exchange
        Mono<String> usernameMono = userContextService.getCurrentUsername(exchange)
                .onErrorResume(e -> {
                    log.warn("Could not extract username from exchange, using SYSTEM: {}", e.getMessage());
                    return Mono.just(SYSTEM_USER);
                });
        
        Mono<String> teamIdMono = teamContextService.getTeamFromContext(exchange)
                .onErrorResume(e -> {
                    log.warn("Could not extract teamId from exchange: {}", e.getMessage());
                    return Mono.just((String) null);
                });
        
        String ipAddress = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : null;
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        
        return Mono.zip(usernameMono, teamIdMono)
                .flatMap(tuple -> {
                    String username = tuple.getT1();
                    String teamId = tuple.getT2();
                    
                    return logEvent(
                            username, // userId and username are same for now
                            username,
                            teamId,
                            ipAddress,
                            userAgent,
                            eventType,
                            action,
                            resourceType,
                            resourceId,
                            null, // documentId
                            null, // collectionId
                            result,
                            metadata,
                            null  // complianceFlags
                    );
                })
                .doOnError(e -> log.error("Failed to log audit event: {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.empty()); // Don't fail the main operation if audit logging fails
    }
    
    @Override
    public Mono<Void> logEvent(
            String userId,
            String username,
            String teamId,
            String ipAddress,
            String userAgent,
            AuditEventType eventType,
            String action,
            String resourceType,
            String resourceId,
            String result,
            AuditLog.AuditMetadata metadata
    ) {
        return logEvent(userId, username, teamId, ipAddress, userAgent, eventType, action,
                resourceType, resourceId, null, null, result, metadata, null);
    }
    
    @Override
    public Mono<Void> logEvent(
            String userId,
            String username,
            String teamId,
            String ipAddress,
            String userAgent,
            AuditEventType eventType,
            String action,
            String resourceType,
            String resourceId,
            String documentId,
            String collectionId,
            String result,
            AuditLog.AuditMetadata metadata,
            AuditLog.ComplianceFlags complianceFlags
    ) {
        AuditLog auditLog = AuditLog.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .eventType(eventType)
                .userId(userId)
                .username(username)
                .teamId(teamId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .action(action)
                .result(result != null ? result : "SUCCESS")
                .resourceType(resourceType)
                .resourceId(resourceId)
                .documentId(documentId)
                .collectionId(collectionId)
                .metadata(metadata)
                .complianceFlags(complianceFlags)
                .archivedAt(null) // Not archived yet
                .s3Key(null)
                .build();
        
        return auditLogRepository.save(auditLog)
                .doOnSuccess(logged -> log.debug("Audit event logged: {} - {} by {} in team {}",
                        eventType, action, username, teamId))
                .doOnError(e -> log.error("Failed to save audit log: {}", e.getMessage(), e))
                .then();
    }
    
    @Override
    public Mono<AuditLogPageResponse> queryAuditLogs(AuditLogQueryRequest request) {
        // Build query based on filters
        Flux<AuditLog> logFlux;
        
        if (request.getDocumentId() != null) {
            // Query by document
            logFlux = auditLogRepository.findByDocumentIdAndTeamIdAndTimestampBetween(
                    request.getDocumentId(),
                    request.getTeamId(),
                    request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().minusDays(90),
                    request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now()
            );
        } else if (request.getUserId() != null) {
            // Query by user
            logFlux = auditLogRepository.findByTeamIdAndUserIdAndTimestampBetween(
                    request.getTeamId(),
                    request.getUserId(),
                    request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().minusDays(90),
                    request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now()
            );
        } else if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) {
            // Query by event types
            logFlux = auditLogRepository.findByTeamIdAndEventTypeInAndTimestampBetween(
                    request.getTeamId(),
                    request.getEventTypes(),
                    request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().minusDays(90),
                    request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now()
            );
        } else if (request.getResult() != null) {
            // Query by result
            logFlux = auditLogRepository.findByTeamIdAndResultAndTimestampBetween(
                    request.getTeamId(),
                    request.getResult(),
                    request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now().minusDays(90),
                    request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now()
            );
        } else {
            // Default: query by team and time range
            LocalDateTime startTime = request.getStartTime() != null ?
                    request.getStartTime() : LocalDateTime.now().minusDays(90);
            LocalDateTime endTime = request.getEndTime() != null ?
                    request.getEndTime() : LocalDateTime.now();
            
            logFlux = auditLogRepository.findByTeamIdAndTimestampBetween(
                    request.getTeamId(),
                    startTime,
                    endTime
            );
        }
        
        // Apply pagination
        // Note: Reactive repositories don't support Pageable directly in all methods
        // We'll do manual pagination after collecting
        return logFlux
                .sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Descending by timestamp
                .collectList()
                .flatMap(allLogs -> {
                    // Manual pagination
                    int page = request.getPage() != null ? request.getPage() : 0;
                    int size = request.getSize() != null ? request.getSize() : 50;
                    int start = page * size;
                    int end = Math.min(start + size, allLogs.size());
                    
                    List<AuditLog> pageContent = allLogs.subList(
                            Math.min(start, allLogs.size()),
                            end
                    );
                    
                    List<AuditLogResponse> responseContent = pageContent.stream()
                            .map(AuditLogResponse::fromEntity)
                            .collect(Collectors.toList());
                    
                    return Mono.just(AuditLogPageResponse.of(
                            responseContent,
                            page,
                            size,
                            allLogs.size()
                    ));
                });
    }
    
    @Override
    public Flux<AuditLog> getDocumentAuditTrail(String documentId, String teamId) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        return auditLogRepository.findByDocumentIdAndTeamIdAndTimestampBetween(
                documentId,
                teamId,
                oneYearAgo,
                LocalDateTime.now()
        ).sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp())); // Ascending for chronological trail
    }
    
    @Override
    public Flux<AuditLog> getUserAuditLogs(
            String userId,
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return auditLogRepository.findByTeamIdAndUserIdAndTimestampBetween(
                teamId,
                userId,
                startTime != null ? startTime : LocalDateTime.now().minusDays(90),
                endTime != null ? endTime : LocalDateTime.now()
        ).sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())); // Descending
    }
}

