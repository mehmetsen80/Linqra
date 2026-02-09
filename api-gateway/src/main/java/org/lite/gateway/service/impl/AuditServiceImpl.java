package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AuditLogPageResponse;
import org.lite.gateway.dto.AuditLogQueryRequest;
import org.lite.gateway.dto.AuditLogResponse;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.repository.AuditLogRepository;
import org.lite.gateway.service.AuditArchivalService;
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
        private final AuditArchivalService auditArchivalService;
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
                        AuditLog.AuditMetadata metadata) {
                // Extract context from exchange
                Mono<String> usernameMono = userContextService.getCurrentUsername(exchange)
                                .onErrorResume(e -> {
                                        log.warn("Could not extract username from exchange, using SYSTEM: {}",
                                                        e.getMessage());
                                        return Mono.just(SYSTEM_USER);
                                });

                Mono<String> teamIdMono = teamContextService.getTeamFromContext(exchange)
                                .onErrorResume(e -> {
                                        log.warn("Could not extract teamId from exchange: {}", e.getMessage());
                                        return Mono.just((String) null);
                                });

                String ipAddress = extractClientIpAddress(exchange);
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
                                                        null // complianceFlags
                                        );
                                })
                                .doOnError(e -> log.error("Failed to log audit event: {}", e.getMessage(), e))
                                .onErrorResume(e -> Mono.empty()); // Don't fail the main operation if audit logging
                                                                   // fails
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
                        AuditLog.AuditMetadata metadata) {
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
                        AuditLog.ComplianceFlags complianceFlags) {
                return Mono.deferContextual(ctx -> {
                        // If IP or UserAgent are null, try to extract from context
                        String finalIpAddress = ipAddress;
                        String finalUserAgent = userAgent;

                        if (finalIpAddress == null || finalUserAgent == null) {
                                ServerWebExchange exchange = ctx.getOrDefault(
                                                org.lite.gateway.filter.ServerWebExchangeContextFilter.EXCHANGE_CONTEXT_KEY,
                                                null);
                                if (exchange != null) {
                                        if (finalIpAddress == null) {
                                                finalIpAddress = extractClientIpAddress(exchange);
                                        }
                                        if (finalUserAgent == null) {
                                                finalUserAgent = exchange.getRequest().getHeaders()
                                                                .getFirst("User-Agent");
                                        }
                                }
                        }

                        AuditLog auditLog = AuditLog.builder()
                                        .eventId(UUID.randomUUID().toString())
                                        .timestamp(LocalDateTime.now())
                                        .eventType(eventType)
                                        .userId(userId)
                                        .username(username)
                                        .teamId(teamId)
                                        .ipAddress(finalIpAddress)
                                        .userAgent(finalUserAgent)
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

                        // Publish to stream for real-time monitoring
                        auditSink.tryEmitNext(auditLog);

                        return auditLogRepository.save(auditLog)
                                        .doOnSuccess(logged -> log.debug("Audit event logged: {} - {} by {} in team {}",
                                                        eventType, action, username, teamId))
                                        .doOnError(e -> log.error("Failed to save audit log: {}", e.getMessage(), e))
                                        .then();
                });
        }

        private final reactor.core.publisher.Sinks.Many<AuditLog> auditSink = reactor.core.publisher.Sinks.many()
                        .multicast().onBackpressureBuffer();

        @Override
        public Flux<AuditLog> getAuditStream() {
                return auditSink.asFlux();
        }

        @Override
        public Mono<AuditLogPageResponse> queryAuditLogs(AuditLogQueryRequest request) {
                // Ensure teamId is present
                if (request.getTeamId() == null || request.getTeamId().trim().isEmpty()) {
                        log.error("Attempted to query audit logs without teamId. Request: {}", request);
                        return Mono.error(new IllegalArgumentException("Team ID is required for audit log queries"));
                }

                log.debug("Querying audit logs for team: {}", request.getTeamId());

                // Build query based on filters
                Flux<AuditLog> logFlux;

                if (request.getDocumentId() != null) {
                        // Query by document
                        logFlux = auditLogRepository.findByDocumentIdAndTeamIdAndTimestampBetween(
                                        request.getDocumentId(),
                                        request.getTeamId(),
                                        request.getStartTime() != null ? request.getStartTime()
                                                        : LocalDateTime.now().minusDays(90),
                                        request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now());
                } else if (request.getUserId() != null) {
                        // Query by user
                        logFlux = auditLogRepository.findByTeamIdAndUserIdAndTimestampBetween(
                                        request.getTeamId(),
                                        request.getUserId(),
                                        request.getStartTime() != null ? request.getStartTime()
                                                        : LocalDateTime.now().minusDays(90),
                                        request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now());
                } else if (request.getEventTypes() != null && !request.getEventTypes().isEmpty()) {
                        // Query by event types
                        logFlux = auditLogRepository.findByTeamIdAndEventTypeInAndTimestampBetween(
                                        request.getTeamId(),
                                        request.getEventTypes(),
                                        request.getStartTime() != null ? request.getStartTime()
                                                        : LocalDateTime.now().minusDays(90),
                                        request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now());
                } else if (request.getResult() != null) {
                        // Query by result
                        logFlux = auditLogRepository.findByTeamIdAndResultAndTimestampBetween(
                                        request.getTeamId(),
                                        request.getResult(),
                                        request.getStartTime() != null ? request.getStartTime()
                                                        : LocalDateTime.now().minusDays(90),
                                        request.getEndTime() != null ? request.getEndTime() : LocalDateTime.now());
                } else {
                        // Default: query by team and time range
                        LocalDateTime startTime = request.getStartTime() != null ? request.getStartTime()
                                        : LocalDateTime.now().minusDays(90);
                        LocalDateTime endTime = request.getEndTime() != null ? request.getEndTime()
                                        : LocalDateTime.now();

                        logFlux = auditLogRepository.findByTeamIdAndTimestampBetween(
                                        request.getTeamId(),
                                        startTime,
                                        endTime);
                }

                // Apply pagination
                // Note: Reactive repositories don't support Pageable directly in all methods
                // We'll do manual pagination after collecting
                return logFlux
                                .sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Descending by timestamp
                                .collectList()
                                .flatMap(mongoLogs -> {
                                        // Check if we should include archived logs
                                        Boolean includeArchived = request.getIncludeArchived() != null
                                                        && request.getIncludeArchived();

                                        if (!includeArchived) {
                                                // Only MongoDB results
                                                return paginateAndReturn(mongoLogs, request);
                                        }

                                        // Merge with S3 archived logs
                                        LocalDateTime startTime = request.getStartTime() != null
                                                        ? request.getStartTime()
                                                        : LocalDateTime.now().minusDays(90);
                                        LocalDateTime endTime = request.getEndTime() != null ? request.getEndTime()
                                                        : LocalDateTime.now();

                                        return auditArchivalService.queryArchivedLogs(
                                                        request.getTeamId(),
                                                        startTime,
                                                        endTime,
                                                        request.getEventTypes(),
                                                        request.getUserId(),
                                                        request.getResult())
                                                        .collectList()
                                                        .map(archivedLogs -> {
                                                                // Merge and deduplicate (by eventId)
                                                                java.util.Map<String, AuditLog> mergedMap = new java.util.LinkedHashMap<>();
                                                                for (AuditLog log : mongoLogs) {
                                                                        mergedMap.put(log.getEventId(), log);
                                                                }
                                                                for (AuditLog log : archivedLogs) {
                                                                        // Don't overwrite MongoDB records
                                                                        mergedMap.putIfAbsent(log.getEventId(), log);
                                                                }
                                                                // Sort by timestamp descending
                                                                List<AuditLog> allLogs = new java.util.ArrayList<>(
                                                                                mergedMap.values());
                                                                allLogs.sort((a, b) -> b.getTimestamp()
                                                                                .compareTo(a.getTimestamp()));
                                                                return allLogs;
                                                        })
                                                        .flatMap(allLogs -> paginateAndReturn(allLogs, request));
                                });
        }

        /**
         * Helper method to paginate logs and return response
         */
        private Mono<AuditLogPageResponse> paginateAndReturn(List<AuditLog> allLogs, AuditLogQueryRequest request) {
                // Manual pagination
                int page = request.getPage() != null ? request.getPage() : 0;
                int size = request.getSize() != null ? request.getSize() : 50;
                int start = page * size;
                int end = Math.min(start + size, allLogs.size());

                List<AuditLog> pageContent = allLogs.subList(
                                Math.min(start, allLogs.size()),
                                end);

                List<AuditLogResponse> responseContent = pageContent.stream()
                                .map(AuditLogResponse::fromEntity)
                                .collect(Collectors.toList());

                return Mono.just(AuditLogPageResponse.of(
                                responseContent,
                                page,
                                size,
                                allLogs.size()));
        }

        @Override
        public Flux<AuditLog> getDocumentAuditTrail(String documentId, String teamId) {
                LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
                return auditLogRepository.findByDocumentIdAndTeamIdAndTimestampBetween(
                                documentId,
                                teamId,
                                oneYearAgo,
                                LocalDateTime.now()).sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp())); // Ascending
                                                                                                                   // for
                                                                                                                   // chronological
                                                                                                                   // trail
        }

        @Override
        public Flux<AuditLog> getUserAuditLogs(
                        String userId,
                        String teamId,
                        LocalDateTime startTime,
                        LocalDateTime endTime) {
                return auditLogRepository.findByTeamIdAndUserIdAndTimestampBetween(
                                teamId,
                                userId,
                                startTime != null ? startTime : LocalDateTime.now().minusDays(90),
                                endTime != null ? endTime : LocalDateTime.now())
                                .sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())); // Descending
        }

        /**
         * Extract client IP address from ServerWebExchange, checking proxy headers
         * first.
         * Checks in order:
         * 1. X-Forwarded-For header (first IP in the list if multiple)
         * 2. X-Real-IP header
         * 3. Remote address from exchange
         * 
         * @param exchange ServerWebExchange
         * @return Client IP address or null if not available
         */
        public String extractClientIpAddress(ServerWebExchange exchange) {
                if (exchange == null || exchange.getRequest() == null) {
                        return null;
                }

                // Check X-Forwarded-For header (most common proxy header)
                // Format: "client, proxy1, proxy2" - we want the first (original client)
                String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
                        // X-Forwarded-For can contain multiple IPs separated by commas
                        String firstIp = forwardedFor.split(",")[0].trim();
                        if (!firstIp.isEmpty()) {
                                return firstIp;
                        }
                }

                // Check X-Real-IP header (alternative proxy header)
                String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
                if (realIp != null && !realIp.trim().isEmpty()) {
                        return realIp.trim();
                }

                // Fall back to remote address
                if (exchange.getRequest().getRemoteAddress() != null) {
                        String remoteIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
                        // Normalize IPv6 loopback to IPv4
                        if ("0:0:0:0:0:0:0:1".equals(remoteIp) || "::1".equals(remoteIp)) {
                                return "127.0.0.1";
                        }
                        return remoteIp;
                }

                return null;
        }
}
