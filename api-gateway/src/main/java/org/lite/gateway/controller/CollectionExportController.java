package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.CollectionExportJob;
import org.lite.gateway.service.CollectionExportService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/collections/export")
@RequiredArgsConstructor
@Tag(name = "Collection Export", description = "Export collections with their documents as decrypted ZIP files")
public class CollectionExportController {
    
    private final CollectionExportService exportService;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;
    
    @PostMapping
    @Operation(summary = "Queue collection export job", 
               description = "Queues an export job for one or more collections. Only ADMIN or SUPER_ADMIN can export. " +
                           "Each collection will be exported as a separate ZIP file containing all documents and processed JSON.")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ResponseEntity<?>> queueExport(
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        log.info("Received collection export request");
        
        @SuppressWarnings("unchecked")
        List<String> collectionIds = (List<String>) request.get("collectionIds");
        
        if (collectionIds == null || collectionIds.isEmpty()) {
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "collectionIds is required and cannot be empty")));
        }
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                        .flatMap(teamId -> {
                            // Check authorization: SUPER_ADMIN or team ADMIN
                            if (user.getRoles().contains("SUPER_ADMIN")) {
                                log.debug("User {} authorized as SUPER_ADMIN to export collections", user.getUsername());
                                return queueExportJob(collectionIds, teamId, user.getUsername());
                            }
                            
                            // For non-SUPER_ADMIN users, check team ADMIN role
                            return teamService.hasRole(teamId, user.getId(), "ADMIN")
                                    .flatMap(hasRole -> {
                                        if (!hasRole) {
                                            log.warn("User {} denied access to export collections for team {} - not a team admin", 
                                                    user.getUsername(), teamId);
                                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                    .body(Map.of("error", "Only team administrators or SUPER_ADMIN can export collections")));
                                        }
                                        
                                        log.debug("User {} authorized as team ADMIN to export collections for team {}", 
                                                user.getUsername(), teamId);
                                        return queueExportJob(collectionIds, teamId, user.getUsername());
                                    });
                        }))
                .onErrorResume(error -> {
                    log.error("Error queueing export job: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to queue export job: " + error.getMessage())));
                });
    }
    
    private Mono<ResponseEntity<?>> queueExportJob(List<String> collectionIds, String teamId, String username) {
        return exportService.queueExport(collectionIds, teamId, username)
                .map(job -> {
                    Map<String, Object> response = Map.of(
                            "jobId", job.getJobId(),
                            "status", job.getStatus(),
                            "collectionIds", collectionIds,
                            "message", "Export job queued successfully"
                    );
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
                });
    }
    
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get export job status", description = "Gets the status of an export job by job ID")
    public Mono<ResponseEntity<CollectionExportJob>> getExportJobStatus(
            @PathVariable String jobId,
            ServerWebExchange exchange) {
        log.info("Getting export job status: {}", jobId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> exportService.getExportJobStatus(jobId, teamId))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error getting export job status: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }
    
    @GetMapping("/jobs")
    @Operation(summary = "Get all export jobs for team", description = "Gets all export jobs for the current team")
    public Flux<CollectionExportJob> getExportJobs(ServerWebExchange exchange) {
        log.info("Getting all export jobs for team");
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMapMany(exportService::getExportJobsForTeam)
                .doOnError(error -> log.error("Error getting export jobs: {}", error.getMessage(), error));
    }
    
    @DeleteMapping("/jobs/{jobId}")
    @Operation(summary = "Cancel export job", 
               description = "Cancels a running export job. Only ADMIN or SUPER_ADMIN can cancel exports.")
    public Mono<ResponseEntity<?>> cancelExportJob(
            @PathVariable String jobId,
            ServerWebExchange exchange) {
        log.info("Cancelling export job: {}", jobId);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                        .flatMap(teamId -> {
                            // Check authorization: SUPER_ADMIN or team ADMIN
                            if (user.getRoles().contains("SUPER_ADMIN")) {
                                log.debug("User {} authorized as SUPER_ADMIN to cancel export job", user.getUsername());
                                return cancelJob(jobId, teamId);
                            }
                            
                            // For non-SUPER_ADMIN users, check team ADMIN role
                            return teamService.hasRole(teamId, user.getId(), "ADMIN")
                                    .flatMap(hasRole -> {
                                        if (!hasRole) {
                                            log.warn("User {} denied access to cancel export job for team {} - not a team admin", 
                                                    user.getUsername(), teamId);
                                            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                                                    .body(Map.of("error", "Only team administrators or SUPER_ADMIN can cancel export jobs")));
                                        }
                                        
                                        log.debug("User {} authorized as team ADMIN to cancel export job for team {}", 
                                                user.getUsername(), teamId);
                                        return cancelJob(jobId, teamId);
                                    });
                        }))
                .onErrorResume(error -> {
                    log.error("Error cancelling export job: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to cancel export job: " + error.getMessage())));
                });
    }
    
    private Mono<ResponseEntity<?>> cancelJob(String jobId, String teamId) {
        return exportService.cancelExportJob(jobId, teamId)
                .map(cancelled -> {
                    if (cancelled) {
                        return ResponseEntity.ok(Map.of(
                                "jobId", jobId,
                                "cancelled", true,
                                "message", "Export job cancelled successfully"
                        ));
                    } else {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Cannot cancel job. It may already be completed, failed, or cancelled."));
                    }
                });
    }
}

