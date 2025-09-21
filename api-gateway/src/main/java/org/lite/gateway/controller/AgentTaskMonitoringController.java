package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.AgentTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-tasks")
@RequiredArgsConstructor
@Slf4j
public class AgentTaskMonitoringController {

    private final AgentAuthContextService agentAuthContextService;
    private final AgentExecutionService agentExecutionService;
    private final AgentTaskService agentTaskService;


    
    @GetMapping("/{taskId}/execution-history")
    public Mono<ResponseEntity<Object>> getTaskExecutionHistory(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "10") int limit,
            ServerWebExchange exchange) {
        
        log.info("Getting execution history for task {} (limit: {})", taskId, limit);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    return agentExecutionService.getTaskExecutionHistory(taskId, limit)
                            .map(execution -> Map.of(
                                    "executionId", execution.getExecutionId(),
                                    "status", execution.getStatus(),
                                    "result", execution.getResult(),
                                    "startedAt", execution.getStartedAt(),
                                    "completedAt", execution.getCompletedAt(),
                                    "executionDurationMs", execution.getExecutionDurationMs(),
                                    "errorMessage", execution.getErrorMessage()
                            ))
                            .collectList()
                            .map(executions -> ResponseEntity.ok((Object) executions));
                })
                .onErrorResume(error -> {
                    log.warn("Authorization or processing failed for getTaskExecutionHistory {}: {}", taskId, error.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) Map.of("error", error.getMessage())));
                });
    }
    
    @GetMapping("/{taskId}/metrics")
    public Mono<ResponseEntity<Object>> getTaskMetrics(
            @PathVariable String taskId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            ServerWebExchange exchange) {
        
        log.info("Getting metrics for task {} from {} to {}", taskId, from, to);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    try {
                        // Set default date range if not provided
                        LocalDateTime fromDate = from != null ? 
                            LocalDateTime.parse(from) : 
                            LocalDateTime.now().minusDays(30); // Last 30 days by default
                        
                        LocalDateTime toDate = to != null ? 
                            LocalDateTime.parse(to) : 
                            LocalDateTime.now(); // Until now
                        
                        log.info("Using date range: {} to {} for task {} metrics in team {}", 
                                fromDate, toDate, taskId, authContext.getTeamId());
                        
                        return agentExecutionService.getTaskExecutionHistory(taskId, 1000) // Get more data for metrics
                                .filter(execution -> {
                                    LocalDateTime startedAt = execution.getStartedAt();
                                    return startedAt != null && 
                                           !startedAt.isBefore(fromDate) && 
                                           !startedAt.isAfter(toDate);
                                })
                                .collectList()
                                .map(executions -> {
                                    // Calculate metrics
                                    long totalExecutions = executions.size();
                                    long successfulExecutions = executions.stream()
                                            .mapToLong(e -> "SUCCESS".equals(e.getResult().toString()) ? 1 : 0)
                                            .sum();
                                    long failedExecutions = totalExecutions - successfulExecutions;
                                    
                                    double successRate = totalExecutions > 0 ? 
                                            (double) successfulExecutions / totalExecutions * 100 : 0;
                                    
                                    double avgDuration = executions.stream()
                                            .filter(e -> e.getExecutionDurationMs() != null)
                                            .mapToLong(e -> e.getExecutionDurationMs())
                                            .average()
                                            .orElse(0.0);
                                    
                                    return Map.of(
                                            "taskId", taskId,
                                            "dateRange", Map.of("from", fromDate, "to", toDate),
                                            "totalExecutions", totalExecutions,
                                            "successfulExecutions", successfulExecutions,
                                            "failedExecutions", failedExecutions,
                                            "successRate", Math.round(successRate * 100.0) / 100.0,
                                            "averageDurationMs", Math.round(avgDuration * 100.0) / 100.0
                                    );
                                })
                                .map(metrics -> ResponseEntity.ok((Object) metrics));
                    } catch (Exception e) {
                        log.error("Error parsing date parameters: {}", e.getMessage());
                        return Mono.just(ResponseEntity.badRequest()
                                .body((Object) Map.of("error", "Invalid date format. Use ISO format: yyyy-MM-ddTHH:mm:ss")));
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Authorization or processing failed for getTaskMetrics {}: {}", taskId, error.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) Map.of("error", error.getMessage())));
                });
    }
    
    @GetMapping("/{taskId}/status")
    public Mono<ResponseEntity<Object>> getTaskStatus(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Getting current status for task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    // Get the most recent execution
                    return agentExecutionService.getTaskExecutionHistory(taskId, 1)
                            .next()
                            .map(execution -> Map.of(
                                    "taskId", taskId,
                                    "currentStatus", execution.getStatus(),
                                    "lastResult", execution.getResult(),
                                    "lastExecutionId", execution.getExecutionId(),
                                    "lastStartedAt", execution.getStartedAt(),
                                    "lastCompletedAt", execution.getCompletedAt(),
                                    "lastDurationMs", execution.getExecutionDurationMs(),
                                    "lastErrorMessage", execution.getErrorMessage() != null ? execution.getErrorMessage() : ""
                            ))
                            .defaultIfEmpty(Map.of(
                                    "taskId", taskId,
                                    "currentStatus", "NEVER_EXECUTED",
                                    "message", "This task has never been executed"
                            ))
                            .map(status -> ResponseEntity.ok((Object) status));
                })
                .onErrorResume(error -> {
                    log.warn("Authorization or processing failed for getTaskStatus {}: {}", taskId, error.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) Map.of("error", error.getMessage())));
                });
    }
    
    @GetMapping("/{taskId}/stats")
    public Mono<ResponseEntity<Object>> getTaskStatistics(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Getting statistics for task {}", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(authContext -> {
                    return agentTaskService.getTaskStatistics(taskId, authContext.getTeamId())
                            .map(stats -> ResponseEntity.ok((Object) stats));
                })
                .onErrorResume(error -> {
                    log.warn("Authorization or processing failed for getTaskStatistics {}: {}", taskId, error.getMessage());
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FORBIDDEN)
                            .body((Object) Map.of("error", error.getMessage())));
                });
    }
} 