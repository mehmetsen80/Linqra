package org.lite.gateway.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentTaskVersion;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.AgentTaskVersionService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/api/agent-tasks")
@RequiredArgsConstructor
@Slf4j
public class AgentTaskVersionController {

    private final AgentTaskVersionService agentTaskVersionService;
    private final AgentTaskService agentTaskService;
    private final AgentService agentService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{taskId}/versions")
    public Mono<ResponseEntity<?>> createNewVersion(
        @PathVariable String taskId,
        @RequestBody String rawRequest,
        @RequestParam(required = false) String changeDescription,
        ServerWebExchange exchange
    ) {
        log.info("Creating new version for agent task: {}", taskId);
        
        try {
            // Parse the request as AgentTask
            JsonNode rootNode = objectMapper.readTree(rawRequest);
            AgentTask updatedTask;
            
            if (rootNode.has("linq_config") || rootNode.has("name") || rootNode.has("description")) {
                // It's an AgentTask structure
                updatedTask = objectMapper.readValue(rawRequest, AgentTask.class);
            } else {
                return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.fromErrorCode(
                        ErrorCode.VALIDATION_ERROR,
                        "Invalid request format: must contain AgentTask fields",
                        HttpStatus.BAD_REQUEST.value()
                    )));
            }
            
            return validateAndCreateNewVersion(taskId, updatedTask, changeDescription, exchange);
        } catch (Exception e) {
            log.error("Error processing agent task version request: ", e);
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    private Mono<ResponseEntity<?>> validateAndCreateNewVersion(
        String taskId,
        AgentTask updatedTask,
        String changeDescription,
        ServerWebExchange exchange
    ) {
        try {
            String description = changeDescription != null ? changeDescription : "Task configuration updated";
            
            return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Set updatedBy for the new version
                    updatedTask.setUpdatedBy(user.getUsername());
                    
                    // Get the current task to check permissions
                    return agentTaskService.getTaskById(taskId)
                        .flatMap(currentTask -> {
                            // Get the agent to check team permissions
                            return agentService.getAgentById(currentTask.getAgentId())
                                .flatMap(agent -> {
                                    // For SUPER_ADMIN, proceed directly
                                    if (user.getRoles().contains("SUPER_ADMIN")) {
                                        return agentTaskVersionService.createNewVersion(taskId, updatedTask, description)
                                            .map(ResponseEntity::ok);
                                    }
                                    
                                    // For non-SUPER_ADMIN users, check team role
                                    return teamService.hasRole(agent.getTeamId(), user.getId(), "ADMIN")
                                        .flatMap(isAdmin -> {
                                            if (!isAdmin) {
                                                return Mono.just(ResponseEntity
                                                    .status(HttpStatus.FORBIDDEN)
                                                    .body(ErrorResponse.fromErrorCode(
                                                        ErrorCode.FORBIDDEN,
                                                        "Only team administrators can create new agent task versions",
                                                        HttpStatus.FORBIDDEN.value()
                                                    )));
                                            }
                                            return agentTaskVersionService.createNewVersion(taskId, updatedTask, description)
                                                .map(ResponseEntity::ok);
                                        });
                                });
                        });
                })
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        Optional.ofNullable(response.getBody())
                            .map(body -> (AgentTaskVersion) body)
                            .ifPresent(version -> log.info("New agent task version created successfully: {}", version.getId()));
                    }
                })
                .doOnError(error -> log.error("Error creating new agent task version: {}", error.getMessage()));
        } catch (Exception e) {
            log.error("Error processing agent task version request: ", e);
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromErrorCode(
                    ErrorCode.VALIDATION_ERROR,
                    "Error processing request: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST.value()
                )));
        }
    }

    @PostMapping("/{taskId}/versions/{version}/rollback")
    public Mono<ResponseEntity<?>> rollbackToVersion(
        @PathVariable String taskId,
        @PathVariable Integer version,
        ServerWebExchange exchange
    ) {
        log.info("Rolling back agent task: {} to version: {}", taskId, version);
        
        return agentTaskService.getTaskById(taskId)
            .flatMap(task -> agentService.getAgentById(task.getAgentId())
                .flatMap(agent -> userContextService.getCurrentUsername(exchange)
                    .flatMap(userService::findByUsername)
                    .flatMap(user -> {
                        // For SUPER_ADMIN, proceed directly
                        if (user.getRoles().contains("SUPER_ADMIN")) {
                            return agentTaskVersionService.rollbackToVersion(taskId, version)
                                .map(ResponseEntity::ok);
                        }
                        
                        // For non-SUPER_ADMIN users, check team role
                        return teamService.hasRole(agent.getTeamId(), user.getId(), "ADMIN")
                            .flatMap(isAdmin -> {
                                if (!isAdmin) {
                                    return Mono.just(ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body(ErrorResponse.fromErrorCode(
                                            ErrorCode.FORBIDDEN,
                                            "Only team administrators can rollback agent task versions",
                                            HttpStatus.FORBIDDEN.value()
                                        )));
                                }
                                return agentTaskVersionService.rollbackToVersion(taskId, version)
                                    .map(ResponseEntity::ok);
                            });
                    })))
            .doOnSuccess(response -> {
                if (response.getStatusCode().is2xxSuccessful()) {
                    Optional.ofNullable(response.getBody())
                        .map(body -> (AgentTask) body)
                        .ifPresent(rolledBackTask -> log.info("Rolled back agent task: {} to version: {}", rolledBackTask.getId(), version));
                }
            })
            .doOnError(error -> log.error("Error rolling back agent task: {}", error.getMessage()));
    }

    @GetMapping("/{taskId}/versions")
    public Flux<AgentTaskVersion> getVersionHistory(@PathVariable String taskId) {
        log.info("Fetching version history for agent task: {}", taskId);
        return agentTaskVersionService.getVersionHistory(taskId)
            .doOnComplete(() -> log.info("Successfully fetched version history for agent task: {}", taskId))
            .doOnError(error -> log.error("Error fetching version history: {}", error.getMessage()));
    }

    @GetMapping("/{taskId}/versions/{version}")
    public Mono<AgentTaskVersion> getVersion(
        @PathVariable String taskId,
        @PathVariable Integer version
    ) {
        log.info("Fetching version: {} for agent task: {}", version, taskId);
        return agentTaskVersionService.getVersion(taskId, version)
            .doOnSuccess(v -> log.info("Fetched version: {} for agent task: {}", v.getId(), taskId))
            .doOnError(error -> log.error("Error fetching version: {}", error.getMessage()));
    }

    @GetMapping("/{taskId}/versions/latest")
    public Mono<AgentTaskVersion> getLatestVersion(@PathVariable String taskId) {
        log.info("Fetching latest version for agent task: {}", taskId);
        return agentTaskVersionService.getLatestVersion(taskId)
            .doOnSuccess(v -> log.info("Fetched latest version for agent task: {}", taskId))
            .doOnError(error -> log.error("Error fetching latest version: {}", error.getMessage()));
    }

    @GetMapping("/{taskId}/versions/count")
    public Mono<ResponseEntity<Long>> getVersionCount(@PathVariable String taskId) {
        log.info("Fetching version count for agent task: {}", taskId);
        return agentTaskVersionService.getVersionCount(taskId)
            .map(ResponseEntity::ok)
            .doOnSuccess(count -> log.info("Version count for agent task {}: {}", taskId, count.getBody()))
            .doOnError(error -> log.error("Error fetching version count: {}", error.getMessage()));
    }

    @PostMapping("/{taskId}/versions/delete-all")
    public Mono<ResponseEntity<String>> deleteAllVersions(
        @PathVariable String taskId,
        @RequestParam String confirmationToken,
        @RequestParam(required = false, defaultValue = "") String reason,
        ServerWebExchange exchange
    ) {
        log.warn("DANGEROUS OPERATION: Request to delete all versions for agent task: {} with reason: {}", taskId, reason);
        
        // Require specific confirmation token
        if (!"DELETE_ALL_VERSIONS_CONFIRMED".equals(confirmationToken)) {
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Invalid confirmation token. This is a destructive operation that requires explicit confirmation."));
        }
        
        if (reason.trim().isEmpty()) {
            return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("Reason is required for deleting all version history."));
        }
        
        return agentTaskService.getTaskById(taskId)
            .flatMap(task -> agentService.getAgentById(task.getAgentId())
                .flatMap(agent -> userContextService.getCurrentUsername(exchange)
                    .flatMap(userService::findByUsername)
                    .flatMap(user -> {
                        // Only SUPER_ADMIN can perform this dangerous operation
                        if (!user.getRoles().contains("SUPER_ADMIN")) {
                            return Mono.just(ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body("Only SUPER_ADMIN can delete all version history. This is a destructive operation."));
                        }
                        
                        log.error("SUPER_ADMIN {} is deleting ALL versions for task {} - Reason: {}", 
                            user.getUsername(), taskId, reason);
                        
                        return agentTaskVersionService.deleteAllVersionsForTask(taskId)
                            .then(Mono.just(ResponseEntity.ok(
                                String.format("WARNING: All version history deleted for task %s by %s. Reason: %s", 
                                    taskId, user.getUsername(), reason))));
                    })))
            .doOnSuccess(response -> {
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.error("CRITICAL: All versions deleted for agent task: {} - {}", taskId, response.getBody());
                }
            })
            .doOnError(error -> log.error("Error deleting all versions: {}", error.getMessage()));
    }

    @DeleteMapping("/{taskId}/versions/duplicates")
    public Mono<ResponseEntity<String>> cleanupDuplicateVersions(
        @PathVariable String taskId,
        ServerWebExchange exchange
    ) {
        log.info("Cleaning up duplicate versions for agent task: {}", taskId);
        
        return agentTaskService.getTaskById(taskId)
            .flatMap(task -> agentService.getAgentById(task.getAgentId())
                .flatMap(agent -> userContextService.getCurrentUsername(exchange)
                    .flatMap(userService::findByUsername)
                    .flatMap(user -> {
                        // For SUPER_ADMIN, proceed directly
                        if (user.getRoles().contains("SUPER_ADMIN")) {
                            return agentTaskVersionService.cleanupDuplicateVersions(taskId)
                                .map(ResponseEntity::ok);
                        }
                        
                        // For non-SUPER_ADMIN users, check team role
                        return teamService.hasRole(agent.getTeamId(), user.getId(), "ADMIN")
                            .flatMap(isAdmin -> {
                                if (!isAdmin) {
                                    return Mono.just(ResponseEntity
                                        .status(HttpStatus.FORBIDDEN)
                                        .body("Only team administrators can cleanup versions"));
                                }
                                return agentTaskVersionService.cleanupDuplicateVersions(taskId)
                                    .map(ResponseEntity::ok);
                            });
                    })))
            .doOnSuccess(response -> log.info("Successfully cleaned up duplicate versions for agent task: {}", taskId))
            .doOnError(error -> log.error("Error cleaning up duplicate versions: {}", error.getMessage()));
    }

} 