package org.lite.gateway.controller;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentTaskVersionService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-tasks")
@RequiredArgsConstructor
@Slf4j
public class AgentTaskController {
    
    private final AgentExecutionService agentExecutionService;
    private final AgentTaskService agentTaskService;
    private final AgentAuthContextService agentAuthContextService;
    private final AgentTaskVersionService agentTaskVersionService;
    private final UserContextService userContextService;
    private final TeamContextService teamContextService;
    
    // ==================== TASK CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<Object>> createTask(
            @RequestBody AgentTask task,
            ServerWebExchange exchange) {
        
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        
        return agentAuthContextService.checkAgentAuthorization(task.getAgentId(), exchange)
                .flatMap(authContext -> {
                    // Set createdBy from the authenticated user
                    task.setCreatedBy(authContext.getUsername());
                    
                    // Inject default teamId and userId into linq_config params if not already present
                    if (task.getLinqConfig() != null) {
                        Map<String, Object> linqConfig = task.getLinqConfig();
                        if (linqConfig.containsKey("query")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
                            if (query.containsKey("params")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> params = (Map<String, Object>) query.get("params");
                                // Only set if not already provided
                                params.putIfAbsent("teamId", authContext.getTeamId());
                                params.putIfAbsent("userId", authContext.getUsername());
                            } else {
                                // Create params if it doesn't exist
                                Map<String, Object> params = new java.util.HashMap<>();
                                params.put("teamId", authContext.getTeamId());
                                params.put("userId", authContext.getUsername());
                                query.put("params", params);
                            }
                        }
                    }
                    
                    return agentTaskService.createTask(task);
                })
                .map(createdTask -> ResponseEntity.ok((Object) createdTask))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for createTask: {}", error.getMessage());
                    ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                            ErrorCode.FORBIDDEN,
                            error.getMessage(),
                            HttpStatus.FORBIDDEN.value()
                    );
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) errorResponse));
                });
    }
    
    @GetMapping("/{taskId}")
    public Mono<ResponseEntity<Object>> getTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Getting task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> agentTaskService.getTaskById(taskId))
                .map(task -> ResponseEntity.ok((Object) task))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for getTask {}: {}", taskId, error.getMessage());
                    ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                            ErrorCode.FORBIDDEN,
                            error.getMessage(),
                            HttpStatus.FORBIDDEN.value()
                    );
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) errorResponse));
                });
    }
    

    
    @DeleteMapping("/{taskId}")
    public Mono<ResponseEntity<Object>> deleteTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Deleting task {} (including all version history)", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    // First delete all version history for this task
                    return agentTaskVersionService.deleteAllVersionsForTask(taskId)
                            .doOnSuccess(v -> log.info("Deleted all version history for task: {}", taskId))
                            .then(agentTaskService.deleteTask(taskId))
                            .doOnSuccess(deleted -> log.info("Successfully deleted task {} and all its versions", taskId));
                })
                .map(deleted -> ResponseEntity.ok((Object) Map.of(
                        "message", "Task and all version history deleted successfully",
                        "taskId", taskId,
                        "deleted", deleted
                )))
                .onErrorResume(error -> {
                    log.warn("Authorization or deletion failed for deleteTask {}: {}", taskId, error.getMessage());
                    ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                            ErrorCode.FORBIDDEN,
                            error.getMessage(),
                            HttpStatus.FORBIDDEN.value()
                    );
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) errorResponse));
                });
    }
    
    // ==================== TASK CONTROL OPERATIONS ====================
    
    @PostMapping("/{taskId}/enable")
    public Mono<ResponseEntity<Object>> enableTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Enabling task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    return agentTaskService.getTaskById(taskId)
                            .flatMap(currentTask -> {
                                if (currentTask.isEnabled()) {
                                    return Mono.error(new IllegalArgumentException("Task is already enabled"));
                                }
                                
                                AgentTask taskUpdates = AgentTask.builder()
                                        .enabled(true)
                                        .updatedBy(authContext.getUsername())
                                        .build();
                                return agentTaskVersionService.createNewVersion(
                                        taskId, 
                                        taskUpdates, 
                                        "Task enabled"
                                );
                            });
                })
                .map(updatedTask -> ResponseEntity.ok((Object) updatedTask))
                .onErrorResume(error -> {
                    if (error instanceof IllegalArgumentException) {
                        log.warn("Validation failed for enableTask {}: {}", taskId, error.getMessage());
                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                ErrorCode.VALIDATION_ERROR,
                                error.getMessage(),
                                HttpStatus.BAD_REQUEST.value()
                        );
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body((Object) errorResponse));
                    } else {
                        log.warn("Authorization or update failed for enableTask {}: {}", taskId, error.getMessage());
                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                ErrorCode.FORBIDDEN,
                                error.getMessage(),
                                HttpStatus.FORBIDDEN.value()
                        );
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body((Object) errorResponse));
                    }
                });
    }
    
    @PostMapping("/{taskId}/disable")
    public Mono<ResponseEntity<Object>> disableTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Disabling task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    return agentTaskService.getTaskById(taskId)
                            .flatMap(currentTask -> {
                                if (!currentTask.isEnabled()) {
                                    return Mono.error(new IllegalArgumentException("Task is already disabled"));
                                }
                                
                                AgentTask taskUpdates = AgentTask.builder()
                                        .enabled(false)
                                        .updatedBy(authContext.getUsername())
                                        .build();
                                return agentTaskVersionService.createNewVersion(
                                        taskId, 
                                        taskUpdates, 
                                        "Task disabled"
                                );
                            });
                })
                .map(updatedTask -> ResponseEntity.ok((Object) updatedTask))
                .onErrorResume(error -> {
                    if (error instanceof IllegalArgumentException) {
                        log.warn("Validation failed for disableTask {}: {}", taskId, error.getMessage());
                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                ErrorCode.VALIDATION_ERROR,
                                error.getMessage(),
                                HttpStatus.BAD_REQUEST.value()
                        );
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body((Object) errorResponse));
                    } else {
                        log.warn("Authorization or update failed for disableTask {}: {}", taskId, error.getMessage());
                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                ErrorCode.FORBIDDEN,
                                error.getMessage(),
                                HttpStatus.FORBIDDEN.value()
                        );
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body((Object) errorResponse));
                    }
                });
    }
    
    // ==================== TASK EXECUTION ====================
    
    @PostMapping("/{taskId}/execute")
    public Mono<ResponseEntity<Object>> executeTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Executing task {} by user from context", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> 
                    agentExecutionService.startTaskExecution(
                            authContext.getAgentId(), 
                            authContext.getTaskId(), 
                            authContext.getTeamId(), 
                            authContext.getUsername())
                    .map(execution -> {
                        Map<String, Object> response = Map.of(
                                "executionId", execution.getExecutionId(),
                                "status", execution.getStatus(),
                                "message", "Task execution started successfully"
                        );
                        return ResponseEntity.ok((Object) response);
                    })
                    .onErrorReturn(ResponseEntity.badRequest().build())
                )
                .onErrorResume(error -> {
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) ErrorResponse.fromErrorCode(
                                    ErrorCode.FORBIDDEN,
                                    error.getMessage(),
                                    HttpStatus.FORBIDDEN.value()
                            )));
                })
                .doOnSuccess(response -> log.info("Task {} execution started successfully", taskId))
                .doOnError(error -> log.error("Failed to execute task {}: {}", taskId, error.getMessage()));
    }

    @PostMapping("/execute-adhoc")
    public Mono<ResponseEntity<Object>> executeAdhocTask(
            @Valid @RequestBody AgentTask agentTask,
            ServerWebExchange exchange) {
        
        log.info("Executing ad-hoc task: {}", agentTask.getName());
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(username -> {
                    // Basic validation
                    if (agentTask.getTaskType() != AgentTaskType.WORKFLOW_EMBEDDED_ADHOC) {
                        return Mono.error(new IllegalArgumentException("Only WORKFLOW_EMBEDDED_ADHOC tasks are supported for ad-hoc execution"));
                    }
                    
                    if (agentTask.getLinqConfig() == null) {
                        return Mono.error(new IllegalArgumentException("Invalid workflow configuration: missing linq_config"));
                    }
                    
                    // Get current team context from JWT token
                    return teamContextService.getTeamFromContext()
                        .flatMap(teamId -> {
                            log.info("Using current team {} for ad-hoc execution", teamId);
                            return agentExecutionService.executeAdhocTask(agentTask, teamId, username);
                        })
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("No current team context found")));
                })
                .map(result -> {
                    Map<String, Object> response = Map.of(
                            "status", "completed",
                            "message", "Ad-hoc task executed successfully",
                            "result", result
                    );
                    return ResponseEntity.ok((Object) response);
                })
                .onErrorResume(error -> {
                    log.error("Failed to execute ad-hoc task: {}", error.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body((Object) ErrorResponse.fromErrorCode(
                                    ErrorCode.VALIDATION_ERROR,
                                    error.getMessage(),
                                    HttpStatus.BAD_REQUEST.value()
                            )));
                })
                .doOnSuccess(response -> log.info("Ad-hoc task executed successfully"))
                .doOnError(error -> log.error("Failed to execute ad-hoc task: {}", error.getMessage()));
    }
    
} 