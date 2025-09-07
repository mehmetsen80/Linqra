package org.lite.gateway.controller;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.service.AgentOrchestrationService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.dto.TaskExecutionRequest;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.service.UserService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-tasks")
@RequiredArgsConstructor
@Slf4j
public class AgentTaskController {
    
    private final AgentOrchestrationService agentOrchestrationService;
    private final AgentTaskService agentTaskService;
    private final AgentService agentService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;
    
    // ==================== TASK CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<AgentTask>> createTask(@RequestBody AgentTask task) {
        
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        
        return agentTaskService.createTask(task)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{taskId}")
    public Mono<ResponseEntity<AgentTask>> getTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting task {} for team {}", taskId, teamId);
        
        return agentTaskService.getTaskById(taskId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{taskId}")
    public Mono<ResponseEntity<AgentTask>> updateTask(
            @PathVariable String taskId,
            @RequestBody AgentTask taskUpdates,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Updating task {} for team {}", taskId, teamId);
        
        return agentTaskService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @DeleteMapping("/{taskId}")
    public Mono<ResponseEntity<Boolean>> deleteTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Deleting task {} for team {}", taskId, teamId);
        
        return agentTaskService.deleteTask(taskId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK LISTING & FILTERING ====================
    
    @GetMapping("/agent/{agentId}")
    public Flux<AgentTask> getTasksByAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting all tasks for agent {} in team {}", agentId, teamId);
        return agentTaskService.getTasksByAgent(agentId, teamId);
    }
    
    // NOTE: Status-based task filtering endpoints removed since task status is now managed by AgentExecution
    // Use AgentExecution endpoints to filter by execution status instead
    // 
    // Removed endpoints:
    // - GET /agent/{agentId}/status/{status} 
    // - GET /agent/{agentId}/ready
    
    // ==================== TASK CONTROL OPERATIONS ====================
    
    @PostMapping("/{taskId}/enable")
    public Mono<ResponseEntity<AgentTask>> enableTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Enabling task {} for team {}", taskId, teamId);
        
        return agentTaskService.setTaskEnabled(taskId, teamId, true)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/disable")
    public Mono<ResponseEntity<AgentTask>> disableTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Disabling task {} for team {}", taskId, teamId);
        
        return agentTaskService.setTaskEnabled(taskId, teamId, false)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK EXECUTION ====================
    
    @PostMapping("/{taskId}/execute")
    public Mono<ResponseEntity<Object>> executeTask(
            @PathVariable String taskId,
            @RequestBody TaskExecutionRequest request,
            ServerWebExchange exchange) {
        
        log.info("Executing task {} by user from context", taskId);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Get the task to get agentId
                    return agentTaskService.getTaskByIdInternal(taskId)
                            .flatMap(task -> {
                                String agentId = task.getAgentId();
                                
                                // Get the agent to get the teamId
                                return agentService.getAgentById(agentId)
                                        .flatMap(agent -> {
                                            String teamId = agent.getTeamId();
                                            
                                            // Check authorization: SUPER_ADMIN or team ADMIN
                                            if (user.getRoles().contains("SUPER_ADMIN")) {
                                                return executeTaskWithAuth(agentId, taskId, teamId, user.getUsername(), exchange);
                                            }
                                            
                                            // For non-SUPER_ADMIN users, check team role
                                            return teamService.hasRole(teamId, user.getId(), "ADMIN")
                                                    .flatMap(isAdmin -> {
                                                        if (!isAdmin) {
                                                            return Mono.just(ResponseEntity
                                                                    .status(HttpStatus.FORBIDDEN)
                                                                    .body((Object) ErrorResponse.fromErrorCode(
                                                                            ErrorCode.FORBIDDEN,
                                                                            "Only team administrators can execute tasks",
                                                                            HttpStatus.FORBIDDEN.value()
                                                                    )));
                                                        }
                                                        return executeTaskWithAuth(agentId, taskId, teamId, user.getUsername(), exchange);
                                                    });
                                        });
                            });
                })
                .doOnSuccess(response -> log.info("Task {} execution started successfully", taskId))
                .doOnError(error -> log.error("Failed to execute task {}: {}", taskId, error.getMessage()));
    }
    
    private Mono<ResponseEntity<Object>> executeTaskWithAuth(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange) {
        return agentOrchestrationService.executeTaskManually(agentId, taskId, teamId, executedBy, exchange)
                .map(execution -> {
                    Map<String, Object> response = Map.of(
                            "executionId", execution.getExecutionId(),
                            "status", execution.getStatus(),
                            "message", "Task execution started successfully"
                    );
                    return ResponseEntity.ok((Object) response);
                })
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    

    
    // ==================== TASK MONITORING ====================
    
    @GetMapping("/{taskId}/performance")
    public Mono<ResponseEntity<Map<String, Object>>> getTaskPerformance(
            @PathVariable String taskId,
            @RequestParam String teamId,
            @RequestParam String from,
            @RequestParam String to) {
        
        log.info("Getting performance metrics for task {} in team {} from {} to {}", 
                taskId, teamId, from, to);
        
        // TODO: Parse date strings to LocalDateTime
        return Mono.just(ResponseEntity.ok(Map.of("message", "Performance metrics endpoint - TODO: implement date parsing")));
    }
    
    @GetMapping("/{taskId}/execution-history")
    public Flux<Map<String, Object>> getTaskExecutionHistory(
            @PathVariable String taskId,
            @RequestParam String teamId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Getting execution history for task {} in team {} (limit: {})", taskId, teamId, limit);
        
        return agentOrchestrationService.getTaskExecutionHistory(taskId, teamId, limit)
                .map(execution -> Map.of(
                        "executionId", execution.getExecutionId(),
                        "status", execution.getStatus(),
                        "result", execution.getResult(),
                        "startedAt", execution.getStartedAt(),
                        "completedAt", execution.getCompletedAt(),
                        "executionDurationMs", execution.getExecutionDurationMs(),
                        "errorMessage", execution.getErrorMessage()
                ));
    }
    
    // ==================== TASK VALIDATION ====================
    
    @PostMapping("/{taskId}/validate")
    public Mono<ResponseEntity<Map<String, Object>>> validateTaskConfiguration(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Validating configuration for task {} in team {}", taskId, teamId);
        
        return agentTaskService.getTaskById(taskId, teamId)
                .flatMap(task -> agentOrchestrationService.validateTaskConfiguration(task))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK DEPENDENCIES ====================
    
    @PostMapping("/{taskId}/dependencies")
    public Mono<ResponseEntity<AgentTask>> updateTaskDependencies(
            @PathVariable String taskId,
            @RequestBody List<String> dependencies,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Updating dependencies for task {} in team {}", taskId, teamId);
        
        AgentTask taskUpdates = new AgentTask();
        taskUpdates.setDependencies(dependencies);
        
        return agentTaskService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{taskId}/dependencies")
    public Mono<ResponseEntity<List<String>>> getTaskDependencies(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting dependencies for task {} in team {}", taskId, teamId);
        
        return agentTaskService.getTaskById(taskId, teamId)
                .map(task -> {
                    List<String> deps = task.getDependencies();
                    return deps != null ? deps : List.<String>of();
                })
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
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
        taskUpdates.setAutoExecute(true);
        
        return agentTaskService.updateTask(taskId, taskUpdates, teamId, updatedBy)
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
        taskUpdates.setCronExpression(null);
        taskUpdates.setAutoExecute(false);
        
        return agentTaskService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK STATISTICS ====================
    
    @GetMapping("/{taskId}/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getTaskStatistics(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting statistics for task {} in team {}", taskId, teamId);
        
        return agentTaskService.getTaskById(taskId, teamId)
                .map(task -> {
                    Map<String, Object> stats = Map.of(
                            "taskId", taskId,
                            "taskName", task.getName(),
                            "enabled", task.isEnabled(),
                            "taskType", task.getTaskType().toString()
                            // TODO: Calculate execution statistics from AgentExecution records
                            // "totalExecutions", executionCount,
                            // "successfulExecutions", successCount,
                            // "failedExecutions", failedCount,
                            // "successRate", successRate,
                            // "averageExecutionTime", avgTime,
                            // "lastExecuted", lastExecutedTime
                    );
                    return stats;
                })
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
} 