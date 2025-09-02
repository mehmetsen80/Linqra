package org.lite.gateway.controller;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskStatus;
import org.lite.gateway.service.AgentOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    
    // ==================== TASK CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<AgentTask>> createTask(@RequestBody AgentTask task) {
        
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        
        return agentOrchestrationService.createTask(task)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{taskId}")
    public Mono<ResponseEntity<AgentTask>> getTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting task {} for team {}", taskId, teamId);
        
        return agentOrchestrationService.getTaskById(taskId, teamId)
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
        
        return agentOrchestrationService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @DeleteMapping("/{taskId}")
    public Mono<ResponseEntity<Boolean>> deleteTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Deleting task {} for team {}", taskId, teamId);
        
        return agentOrchestrationService.deleteTask(taskId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK LISTING & FILTERING ====================
    
    @GetMapping("/agent/{agentId}")
    public Flux<AgentTask> getTasksByAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting all tasks for agent {} in team {}", agentId, teamId);
        return agentOrchestrationService.getTasksByAgent(agentId, teamId);
    }
    
    @GetMapping("/agent/{agentId}/status/{status}")
    public Flux<AgentTask> getTasksByAgentAndStatus(
            @PathVariable String agentId,
            @PathVariable AgentTaskStatus status,
            @RequestParam String teamId) {
        
        log.info("Getting tasks with status {} for agent {} in team {}", status, agentId, teamId);
        return agentOrchestrationService.getTasksByAgentAndStatus(agentId, teamId, status);
    }
    
    @GetMapping("/agent/{agentId}/ready")
    public Flux<AgentTask> getTasksReadyToExecute(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting tasks ready to execute for agent {} in team {}", agentId, teamId);
        return agentOrchestrationService.getTasksByAgentAndStatus(agentId, teamId, AgentTaskStatus.READY);
    }
    
    // ==================== TASK CONTROL OPERATIONS ====================
    
    @PostMapping("/{taskId}/enable")
    public Mono<ResponseEntity<AgentTask>> enableTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Enabling task {} for team {}", taskId, teamId);
        
        return agentOrchestrationService.setTaskEnabled(taskId, teamId, true)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/disable")
    public Mono<ResponseEntity<AgentTask>> disableTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Disabling task {} for team {}", taskId, teamId);
        
        return agentOrchestrationService.setTaskEnabled(taskId, teamId, false)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK EXECUTION ====================
    
    @PostMapping("/{taskId}/execute")
    public Mono<ResponseEntity<Map<String, Object>>> executeTask(
            @PathVariable String taskId,
            @RequestParam String agentId,
            @RequestParam String teamId,
            @RequestParam String executedBy) {
        
        log.info("Executing task {} for agent {} in team {}", taskId, agentId, teamId);
        
        return agentOrchestrationService.executeTaskManually(agentId, taskId, teamId, executedBy)
                .map(execution -> {
                    Map<String, Object> response = Map.of(
                            "executionId", execution.getExecutionId(),
                            "status", execution.getStatus(),
                            "message", "Task execution started successfully"
                    );
                    return response;
                })
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/execute-with-params")
    public Mono<ResponseEntity<Map<String, Object>>> executeTaskWithParams(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> executionParams,
            @RequestParam String agentId,
            @RequestParam String teamId,
            @RequestParam String executedBy) {
        
        log.info("Executing task {} with params for agent {} in team {}", taskId, agentId, teamId);
        
        // TODO: Implement parameter passing to task execution
        Map<String, Object> response = Map.of(
                "message", "Task execution with params - TODO: implement parameter passing",
                "taskId", taskId,
                "params", executionParams
        );
        return Mono.just(ResponseEntity.ok(response));
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
        
        return agentOrchestrationService.getTaskById(taskId, teamId)
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
        
        return agentOrchestrationService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{taskId}/dependencies")
    public Mono<ResponseEntity<List<String>>> getTaskDependencies(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting dependencies for task {} in team {}", taskId, teamId);
        
        return agentOrchestrationService.getTaskById(taskId, teamId)
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
        
        return agentOrchestrationService.updateTask(taskId, taskUpdates, teamId, updatedBy)
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
        
        return agentOrchestrationService.updateTask(taskId, taskUpdates, teamId, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== TASK STATISTICS ====================
    
    @GetMapping("/{taskId}/stats")
    public Mono<ResponseEntity<Map<String, Object>>> getTaskStatistics(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Getting statistics for task {} in team {}", taskId, teamId);
        
        return agentOrchestrationService.getTaskById(taskId, teamId)
                .map(task -> {
                    Map<String, Object> stats = Map.of(
                            "taskId", taskId,
                            "totalExecutions", task.getTotalExecutions(),
                            "successfulExecutions", task.getSuccessfulExecutions(),
                            "failedExecutions", task.getFailedExecutions(),
                            "successRate", task.getSuccessRate(),
                            "averageExecutionTime", task.getAverageExecutionTime(),
                            "lastExecuted", task.getLastExecuted(),
                            "nextExecution", task.getNextExecution()
                    );
                    return stats;
                })
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
} 