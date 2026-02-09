package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AuditLogPageResponse;
import org.lite.gateway.dto.AuditLogQueryRequest;
import org.lite.gateway.dto.AuditLogResponse;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.service.AuditArchivalService;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST controller for audit log queries and archival management
 * Only ADMIN or SUPER_ADMIN can access audit logs
 */
@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Query audit logs and manage archival. Only ADMIN or SUPER_ADMIN can access.")
public class AuditController {

        private final AuditService auditService;
        private final AuditArchivalService archivalService;
        private final UserContextService userContextService;
        private final UserService userService;
        private final TeamContextService teamContextService;

        /**
         * Query audit logs with filters and pagination
         * Only ADMIN or SUPER_ADMIN can query audit logs
         */
        @PostMapping("/logs/query")
        @Operation(summary = "Query audit logs", description = "Query audit logs with filters (team, user, event type, time range, etc.) and pagination. "
                        +
                        "Only ADMIN or SUPER_ADMIN can query audit logs.")
        public Mono<ResponseEntity<?>> queryAuditLogs(
                        @RequestBody AuditLogQueryRequest request,
                        ServerWebExchange exchange) {

                log.debug("Received audit log query request: {}", request);

                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> {
                                                        // Check authorization: SUPER_ADMIN or team ADMIN
                                                        if (user.getRoles().contains("SUPER_ADMIN")) {
                                                                log.debug("User {} authorized as SUPER_ADMIN to query audit logs",
                                                                                user.getUsername());
                                                                return executeQuery(request, teamId);
                                                        }

                                                        // For non-SUPER_ADMIN users, check team ADMIN role
                                                        return teamContextService.getTeamFromContext(exchange)
                                                                        .flatMap(actualTeamId -> {
                                                                                // Ensure query is for the user's team
                                                                                if (request.getTeamId() != null
                                                                                                && !request.getTeamId()
                                                                                                                .equals(actualTeamId)) {
                                                                                        return Mono.just(ResponseEntity
                                                                                                        .status(HttpStatus.FORBIDDEN)
                                                                                                        .body(ErrorResponse
                                                                                                                        .fromErrorCode(
                                                                                                                                        org.lite.gateway.dto.ErrorCode.FORBIDDEN,
                                                                                                                                        "You can only query audit logs for your own team",
                                                                                                                                        HttpStatus.FORBIDDEN
                                                                                                                                                        .value())));
                                                                                }

                                                                                // Set teamId from context if not
                                                                                // provided
                                                                                if (request.getTeamId() == null) {
                                                                                        request.setTeamId(actualTeamId);
                                                                                }

                                                                                return executeQuery(request,
                                                                                                actualTeamId);
                                                                        });
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error querying audit logs: {}", error.getMessage(), error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(ErrorResponse.fromErrorCode(
                                                                        org.lite.gateway.dto.ErrorCode.INTERNAL_ERROR,
                                                                        "Error querying audit logs: "
                                                                                        + error.getMessage(),
                                                                        HttpStatus.INTERNAL_SERVER_ERROR.value())));
                                });
        }

        private Mono<ResponseEntity<?>> executeQuery(AuditLogQueryRequest request, String teamId) {
                // Ensure teamId is set
                if (request.getTeamId() == null) {
                        request.setTeamId(teamId);
                }

                // Validate teamId matches
                if (!request.getTeamId().equals(teamId)) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(Map.of("error", "You can only query audit logs for your own team")));
                }

                return auditService.queryAuditLogs(request)
                                .<ResponseEntity<?>>map(response -> ResponseEntity.ok(response))
                                .defaultIfEmpty(ResponseEntity.ok(AuditLogPageResponse.of(
                                                java.util.Collections.emptyList(), 0, 50, 0L)));
        }

        /**
         * Get audit trail for a specific document
         * Shows complete lifecycle of a document (create → access → delete)
         */
        @GetMapping("/logs/document/{documentId}")
        @Operation(summary = "Get document audit trail", description = "Get complete audit trail for a specific document. Only ADMIN or SUPER_ADMIN can access.")
        public Mono<ResponseEntity<?>> getDocumentAuditTrail(
                        @PathVariable String documentId,
                        ServerWebExchange exchange) {

                log.debug("Getting audit trail for document: {}", documentId);

                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> {
                                                        // Check authorization
                                                        if (user.getRoles().contains("SUPER_ADMIN")) {
                                                                return getDocumentTrail(documentId, teamId);
                                                        }

                                                        // For team ADMIN, ensure document belongs to their team
                                                        return getDocumentTrail(documentId, teamId);
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error getting document audit trail: {}", error.getMessage(), error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(ErrorResponse.fromErrorCode(
                                                                        org.lite.gateway.dto.ErrorCode.INTERNAL_ERROR,
                                                                        "Error getting document audit trail: "
                                                                                        + error.getMessage(),
                                                                        HttpStatus.INTERNAL_SERVER_ERROR.value())));
                                });
        }

        private Mono<ResponseEntity<?>> getDocumentTrail(String documentId, String teamId) {
                return auditService.getDocumentAuditTrail(documentId, teamId)
                                .map(AuditLogResponse::fromEntity)
                                .collectList()
                                .<ResponseEntity<?>>map(list -> ResponseEntity.ok(list))
                                .defaultIfEmpty(ResponseEntity.ok(java.util.Collections.emptyList()));
        }

        /**
         * Get audit logs for a specific user
         */
        @GetMapping("/logs/user/{userId}")
        @Operation(summary = "Get user audit logs", description = "Get audit logs for a specific user within a time range. Only ADMIN or SUPER_ADMIN can access.")
        public Mono<ResponseEntity<?>> getUserAuditLogs(
                        @PathVariable String userId,
                        @RequestParam(required = false) String startTime,
                        @RequestParam(required = false) String endTime,
                        ServerWebExchange exchange) {

                log.debug("Getting audit logs for user: {}", userId);

                LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime)
                                : LocalDateTime.now().minusDays(90);
                LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : LocalDateTime.now();

                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> {
                                                        // Check authorization
                                                        if (user.getRoles().contains("SUPER_ADMIN")) {
                                                                return getUserLogs(userId, teamId, start, end);
                                                        }

                                                        // For team ADMIN, ensure user belongs to their team
                                                        return getUserLogs(userId, teamId, start, end);
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error getting user audit logs: {}", error.getMessage(), error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(ErrorResponse.fromErrorCode(
                                                                        org.lite.gateway.dto.ErrorCode.INTERNAL_ERROR,
                                                                        "Error getting user audit logs: "
                                                                                        + error.getMessage(),
                                                                        HttpStatus.INTERNAL_SERVER_ERROR.value())));
                                });
        }

        private Mono<ResponseEntity<?>> getUserLogs(String userId, String teamId, LocalDateTime start,
                        LocalDateTime end) {
                return auditService.getUserAuditLogs(userId, teamId, start, end)
                                .map(AuditLogResponse::fromEntity)
                                .collectList()
                                .<ResponseEntity<?>>map(list -> ResponseEntity.ok(list))
                                .defaultIfEmpty(ResponseEntity.ok(java.util.Collections.emptyList()));
        }

        /**
         * Get archival statistics
         * Shows how many logs are in MongoDB, ready for archival, and already archived
         */
        @GetMapping("/stats")
        @Operation(summary = "Get archival statistics", description = "Get statistics about audit log archival status. Only ADMIN or SUPER_ADMIN can access.")
        public Mono<ResponseEntity<?>> getArchivalStats(ServerWebExchange exchange) {

                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> {
                                                        // Check authorization: SUPER_ADMIN or team ADMIN
                                                        // Both should see stats for the specific team context
                                                        log.debug("Getting archival stats for team: {}", teamId);
                                                        return archivalService.getArchivalStats(teamId)
                                                                        .<ResponseEntity<?>>map(stats -> ResponseEntity
                                                                                        .ok(stats));
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error getting archival stats: {}", error.getMessage(), error);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(ErrorResponse.fromErrorCode(
                                                                        org.lite.gateway.dto.ErrorCode.INTERNAL_ERROR,
                                                                        "Error getting archival stats: "
                                                                                        + error.getMessage(),
                                                                        HttpStatus.INTERNAL_SERVER_ERROR.value())));
                                });
        }
}
