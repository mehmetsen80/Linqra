package org.lite.gateway.controller;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.ExecutionTrigger;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.AgentMonitoringService;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentTaskVersionService;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/agent-tasks")
@RequiredArgsConstructor
@Slf4j
public class AgentTaskController {
    
    private final AgentExecutionService agentExecutionService;
    private final AgentTaskService agentTaskService;
    private final AgentMonitoringService agentMonitoringService;
    private final AgentAuthContextService agentAuthContextService;
    private final AgentTaskVersionService agentTaskVersionService;
    
    // ==================== TASK CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<Object>> createTask(
            @RequestBody AgentTask task,
            ServerWebExchange exchange) {
        
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        
        return agentAuthContextService.checkAgentAuthorization(task.getAgentId(), exchange)
                .flatMap(authContext -> agentTaskService.createTask(task))
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
    
    @PutMapping("/{taskId}")
    public Mono<ResponseEntity<Object>> updateTask(
            @PathVariable String taskId,
            @RequestBody AgentTask taskUpdates,
            ServerWebExchange exchange) {
        
        log.info("Updating task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> agentTaskService.updateTask(taskId, taskUpdates, authContext.getUsername()))
                .map(updatedTask -> ResponseEntity.ok((Object) updatedTask))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for updateTask {}: {}", taskId, error.getMessage());
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
    
    // ==================== TASK LISTING & FILTERING ====================
    
    @GetMapping("/agent/{agentId}")
    public Mono<ResponseEntity<Object>> getTasksByAgent(
            @PathVariable String agentId,
            ServerWebExchange exchange) {
        
        log.info("Getting all tasks for agent {}", agentId);
        
        return agentAuthContextService.checkAgentAuthorization(agentId, exchange)
                .map(authContext -> ResponseEntity.ok((Object) agentTaskService.getTasksByAgent(agentId, authContext.getTeamId())))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for getTasksByAgent {}: {}", agentId, error.getMessage());
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
                .flatMap(authContext -> agentTaskService.setTaskEnabled(taskId, true))
                .map(enabledTask -> ResponseEntity.ok((Object) enabledTask))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for enableTask {}: {}", taskId, error.getMessage());
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
    
    @PostMapping("/{taskId}/disable")
    public Mono<ResponseEntity<Object>> disableTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Disabling task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> agentTaskService.setTaskEnabled(taskId, false))
                .map(disabledTask -> ResponseEntity.ok((Object) disabledTask))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for disableTask {}: {}", taskId, error.getMessage());
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
                            authContext.getUsername(), 
                            exchange)
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

    
    // ==================== TASK SCHEDULING ====================
    
    @PostMapping("/{taskId}/schedule")
    public Mono<ResponseEntity<AgentTask>> scheduleTask(
            @PathVariable String taskId,
            @RequestParam String cronExpression,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Scheduling task {} with cron: {} for team {}", taskId, cronExpression, teamId);
        
        AgentTask taskUpdates = new AgentTask();
        taskUpdates.setCronExpression(cronExpression);
        taskUpdates.setExecutionTrigger(ExecutionTrigger.CRON);
        
        return agentTaskService.updateTask(taskId, taskUpdates, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/unschedule")
    public Mono<ResponseEntity<AgentTask>> unscheduleTask(
            @PathVariable String taskId,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Unschedule task {} for team {}", taskId, teamId);
        
        AgentTask taskUpdates = new AgentTask();
        taskUpdates.setCronExpression("");
        taskUpdates.setExecutionTrigger(ExecutionTrigger.MANUAL);
        
        return agentTaskService.updateTask(taskId, taskUpdates, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK STATISTICS ====================
    
    @GetMapping("/{taskId}/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getTaskStatistics(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting statistics for task {} in team {}", taskId, teamId);
        
        return agentTaskService.getTaskStatistics(taskId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
} 