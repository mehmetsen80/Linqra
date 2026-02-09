package org.lite.gateway.controller;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.service.AgentMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agents/monitoring")
@RequiredArgsConstructor
@Slf4j
public class AgentMonitoringController {

    private final AgentMonitoringService agentMonitoringService;

    // ==================== AGENT HEALTH MONITORING ====================

    @GetMapping("/{agentId}/health")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentHealth(@PathVariable String agentId) {

        log.info("Getting health status for agent {}", agentId);

        return agentMonitoringService.getAgentHealth(agentId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error getting agent health for {}: {}", agentId, error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(500)
                            .body(Map.of("error", error.getMessage())));
                });
    }

    @GetMapping("/team/{teamId}/health")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamAgentsHealth(@PathVariable String teamId) {
        log.info("Getting health summary for all agents in team {}", teamId);

        return agentMonitoringService.getTeamAgentsHealth(teamId)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error getting team agents health for team {}: {}", teamId, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", throwable.getMessage())));
                });
    }

    @GetMapping("/team/{teamId}/errors")
    public Flux<Agent> getAgentsWithErrors(@PathVariable String teamId) {
        log.info("Getting agents with recent errors for team {}", teamId);

        return agentMonitoringService.getAgentsWithErrors(teamId);
    }

    // ==================== PERFORMANCE MONITORING ====================

    @GetMapping("/{agentId}/performance")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentPerformance(
            @PathVariable String agentId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        try {
            // Default to last month if not provided
            LocalDateTime toDate = (to != null) ? LocalDateTime.parse(to) : LocalDateTime.now();
            LocalDateTime fromDate = (from != null) ? LocalDateTime.parse(from) : toDate.minusMonths(1);

            log.info("Getting performance metrics for agent {} from {} to {}",
                    agentId, fromDate, toDate);

            return agentMonitoringService.getAgentPerformance(agentId, fromDate, toDate)
                    .map(ResponseEntity::ok)
                    .onErrorResume(throwable -> {
                        log.error("Error getting agent performance for agent {}: {}", agentId, throwable.getMessage());
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.of("error", throwable.getMessage())));
                    });
        } catch (Exception e) {
            log.error("Error parsing date parameters: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid date format. Use ISO format: yyyy-MM-ddTHH:mm:ss")));
        }
    }

    @GetMapping("/team/{teamId}/execution-stats")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamExecutionStats(
            @PathVariable String teamId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String agentId) {

        try {
            // Default to last month if not provided
            LocalDateTime toDate = (to != null) ? LocalDateTime.parse(to) : LocalDateTime.now();
            LocalDateTime fromDate = (from != null) ? LocalDateTime.parse(from) : toDate.minusMonths(1);

            log.info("Getting execution statistics for team {} from {} to {} (agentId: {})", teamId, fromDate, toDate,
                    agentId);

            return agentMonitoringService.getTeamExecutionStats(teamId, fromDate, toDate, agentId)
                    .map(ResponseEntity::ok)
                    .onErrorResume(throwable -> {
                        log.error("Error getting team execution stats for team {}: {}", teamId, throwable.getMessage());
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Map.of("error", throwable.getMessage())));
                    });
        } catch (Exception e) {
            log.error("Error parsing date parameters: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid date format. Use ISO format: yyyy-MM-ddTHH:mm:ss")));
        }
    }

    // ==================== RESOURCE MONITORING ====================

    @GetMapping("/team/{teamId}/resource-usage")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamResourceUsage(@PathVariable String teamId) {
        log.info("Getting resource usage for team {}", teamId);

        return agentMonitoringService.getTeamResourceUsage(teamId)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error getting team resource usage for team {}: {}", teamId, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", throwable.getMessage())));
                });
    }

    @GetMapping("/team/{teamId}/capabilities")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentCapabilitiesSummary(@PathVariable String teamId) {
        log.info("Getting capabilities summary for team {}", teamId);

        return agentMonitoringService.getAgentCapabilitiesSummary(teamId)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error getting agent capabilities summary for team {}: {}", teamId,
                            throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", throwable.getMessage())));
                });
    }

    // ==================== EXECUTION MONITORING ====================

    @GetMapping("/team/{teamId}/failed-executions")
    public Flux<Map<String, Object>> getFailedExecutions(
            @PathVariable String teamId,
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Getting failed executions for team {} (limit: {})", teamId, limit);

        return agentMonitoringService.getFailedExecutions(teamId, limit)
                .map(execution -> Map.of(
                        "executionId", execution.getExecutionId(),
                        "agentId", execution.getAgentId(),
                        "taskId", execution.getTaskId(),
                        "taskName", execution.getTaskName(),
                        "status", execution.getStatus(),
                        "result", execution.getResult(),
                        "errorMessage", execution.getErrorMessage() != null ? execution.getErrorMessage() : "",
                        "startedAt", execution.getStartedAt(),
                        "completedAt", execution.getCompletedAt()));
    }

    @GetMapping("/workflow/{workflowExecutionId}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getWorkflowExecutionStatus(
            @PathVariable String workflowExecutionId) {

        log.info("Getting workflow execution status {}", workflowExecutionId);

        return agentMonitoringService.getWorkflowExecutionStatus(workflowExecutionId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping("/{agentId}/tasks-stats")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentTasksStatistics(
            @PathVariable String agentId,
            @RequestParam String teamId) {

        log.info("Getting task-level statistics for agent {} in team {}", agentId, teamId);

        return agentMonitoringService.getTaskStatisticsByAgent(agentId, teamId)
                .map(ResponseEntity::ok)
                .onErrorResume(throwable -> {
                    log.error("Error getting task statistics for agent {}: {}", agentId, throwable.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(Map.of("error", throwable.getMessage())));
                });
    }
}