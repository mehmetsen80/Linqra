package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentMonitoringService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.enums.ExecutionResult;
import org.lite.gateway.enums.ExecutionStatus;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMonitoringServiceImpl implements AgentMonitoringService {
    
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final LinqWorkflowExecutionService workflowExecutionService;
    
    // ==================== HEALTH MONITORING ====================
    
    @Override
    public Mono<Map<String, Object>> getAgentHealth(String agentId, String teamId) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    // Get latest execution for last run and error info
                    Mono<AgentExecution> latestExecutionMono = agentExecutionRepository
                            .findByAgentIdOrderByCreatedAtDesc(agentId)
                            .next()
                            .switchIfEmpty(Mono.empty());
                    
                    // Get next scheduled task run time
                    Mono<LocalDateTime> nextRunMono = agentTaskRepository
                            .findTasksReadyToRunByAgent(agentId, LocalDateTime.now().plusYears(1)) // future tasks
                            .map(AgentTask::getNextRun)
                            .filter(nextRun -> nextRun != null && nextRun.isAfter(LocalDateTime.now()))
                            .sort()
                            .next()
                            .switchIfEmpty(Mono.empty());
                    
                    return Mono.zip(latestExecutionMono.defaultIfEmpty(new AgentExecution()), nextRunMono.defaultIfEmpty(null))
                            .map(tuple -> {
                                AgentExecution latestExecution = tuple.getT1();
                                LocalDateTime nextRun = tuple.getT2();
                                
                                Map<String, Object> health = Map.of(
                                        "agentId", agentId,
                                        "enabled", agent.isEnabled(),
                                        "lastRun", latestExecution.getStartedAt(),
                                        "nextRun", nextRun,
                                        "lastError", latestExecution.getErrorMessage(),
                                        "canExecute", agent.canExecute()
                                );
                                
                                return health;
                            });
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamAgentsHealth(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .flatMap(agents -> {
                    long totalAgents = agents.size();
                    long enabledAgents = agents.stream().filter(Agent::isEnabled).count();
                    
                    if (agents.isEmpty()) {
                        return Mono.just(Map.of(
                                "teamId", teamId,
                                "totalAgents", 0L,
                                "enabledAgents", 0L,
                                "runningAgents", 0L,
                                "errorAgents", 0L,
                                "healthPercentage", 0.0
                        ));
                    }
                    
                    // Get agent IDs for this team
                    List<String> agentIds = agents.stream().map(Agent::getId).toList();
                    LocalDateTime since = LocalDateTime.now().minusHours(24);
                    
                    // Count running agents (agents with RUNNING executions)
                    Mono<Long> runningAgentsMono = agentExecutionRepository.findByStatus(ExecutionStatus.RUNNING.name())
                            .filter(execution -> agentIds.contains(execution.getAgentId()))
                            .map(execution -> execution.getAgentId())
                            .distinct()
                            .count();
                    
                    // Count error agents (agents with recent FAILED executions)
                    Mono<Long> errorAgentsMono = agentExecutionRepository.findByStatus(ExecutionStatus.FAILED.name())
                            .filter(execution -> agentIds.contains(execution.getAgentId()))
                            .filter(execution -> execution.getStartedAt() != null && execution.getStartedAt().isAfter(since))
                            .map(execution -> execution.getAgentId())
                            .distinct()
                            .count();
                    
                    return Mono.zip(runningAgentsMono, errorAgentsMono)
                            .map(tuple -> {
                                long runningAgents = tuple.getT1();
                                long errorAgents = tuple.getT2();
                                
                                return Map.of(
                                        "teamId", teamId,
                                        "totalAgents", totalAgents,
                                        "enabledAgents", enabledAgents,
                                        "runningAgents", runningAgents,
                                        "errorAgents", errorAgents,
                                        "healthPercentage", totalAgents > 0 ? (enabledAgents * 100.0 / totalAgents) : 0.0
                                );
                            });
                });
    }
    
    @Override
    public Flux<Agent> getAgentsWithErrors(String teamId) {
        // Find agents with recent failed executions (within last 24 hours)
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        
        return agentExecutionRepository.findByStatus(ExecutionStatus.FAILED.name())
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .filter(execution -> execution.getStartedAt() != null && execution.getStartedAt().isAfter(since))
                .map(execution -> execution.getAgentId())
                .distinct()
                .flatMap(agentId -> agentRepository.findById(agentId))
                .filter(agent -> teamId.equals(agent.getTeamId())); // Double-check team access
    }
    
    // ==================== PERFORMANCE MONITORING ====================
    
    @Override
    public Mono<Map<String, Object>> getAgentPerformance(String agentId, String teamId, LocalDateTime from, LocalDateTime to) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .thenMany(agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to))
                .collectList()
                .map(executions -> {
                    long totalExecutions = executions.size();
                    long successfulExecutions = executions.stream()
                            .filter(e -> ExecutionResult.SUCCESS.name().equals(e.getResult()))
                            .count();
                    long failedExecutions = executions.stream()
                            .filter(e -> ExecutionResult.FAILURE.name().equals(e.getResult()))
                            .count();
                    
                    double avgExecutionTime = executions.stream()
                            .filter(e -> e.getExecutionDurationMs() != null)
                            .mapToLong(AgentExecution::getExecutionDurationMs)
                            .average()
                            .orElse(0.0);
                    
                    return Map.of(
                            "agentId", agentId,
                            "totalExecutions", totalExecutions,
                            "successfulExecutions", successfulExecutions,
                            "failedExecutions", failedExecutions,
                            "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                            "averageExecutionTimeMs", avgExecutionTime
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTaskPerformance(String taskId, String teamId, LocalDateTime from, LocalDateTime to) {
        return agentTaskRepository.findById(taskId)
                .thenMany(agentExecutionRepository.findByTaskIdAndStartedAtBetween(taskId, from, to))
                .collectList()
                .map(executions -> {
                    long totalExecutions = executions.size();
                    long successfulExecutions = executions.stream()
                            .filter(e -> ExecutionResult.SUCCESS.name().equals(e.getResult()))
                            .count();
                    long failedExecutions = executions.stream()
                            .filter(e -> ExecutionResult.FAILURE.name().equals(e.getResult()))
                            .count();
                    
                    double avgExecutionTime = executions.stream()
                            .filter(e -> e.getExecutionDurationMs() != null)
                            .mapToLong(AgentExecution::getExecutionDurationMs)
                            .average()
                            .orElse(0.0);
                    
                    return Map.of(
                            "taskId", taskId,
                            "totalExecutions", totalExecutions,
                            "successfulExecutions", successfulExecutions,
                            "failedExecutions", failedExecutions,
                            "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                            "averageExecutionTimeMs", avgExecutionTime
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to) {
        // First get all agents for the team, then get their executions
        return agentRepository.findByTeamId(teamId)
                .map(Agent::getId)
                .collectList()
                .flatMap(agentIds -> {
                    if (agentIds.isEmpty()) {
                        return Mono.just(Map.of(
                                "teamId", teamId,
                                "totalExecutions", 0L,
                                "successfulExecutions", 0L,
                                "failedExecutions", 0L,
                                "successRate", 0.0,
                                "period", Map.of("from", from, "to", to)
                        ));
                    }
                    
                    return Flux.fromIterable(agentIds)
                            .flatMap(agentId -> agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to))
                            .collectList()
                            .map(executions -> {
                                long totalExecutions = executions.size();
                                long successfulExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.SUCCESS.name().equals(e.getResult()))
                                        .count();
                                long failedExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.FAILURE.name().equals(e.getResult()))
                                        .count();
                                
                                return Map.of(
                                        "teamId", teamId,
                                        "totalExecutions", totalExecutions,
                                        "successfulExecutions", successfulExecutions,
                                        "failedExecutions", failedExecutions,
                                        "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                                        "period", Map.of("from", from, "to", to)
                                );
                            });
                });
    }
    
    // ==================== RESOURCE MONITORING ====================
    
    @Override
    public Mono<Map<String, Object>> getAgentCapabilitiesSummary(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .map(agents -> {
                    Map<String, Long> capabilityCounts = agents.stream()
                            .flatMap(agent -> agent.getCapabilities().stream())
                            .collect(Collectors.groupingBy(
                                    capability -> capability.name(),
                                    Collectors.counting()
                            ));
                    
                    return Map.of(
                            "teamId", teamId,
                            "totalAgents", agents.size(),
                            "capabilityCounts", capabilityCounts
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamResourceUsage(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .flatMap(agents -> {
                    long totalAgents = agents.size();
                    long scheduledAgents = agents.stream()
                            .filter(Agent::isScheduled)
                            .count();
                    
                    if (agents.isEmpty()) {
                        return Mono.just(Map.of(
                                "teamId", teamId,
                                "totalAgents", 0L,
                                "runningAgents", 0L,
                                "scheduledAgents", 0L,
                                "utilizationPercentage", 0.0
                        ));
                    }
                    
                    // Get agent IDs for this team
                    List<String> agentIds = agents.stream().map(Agent::getId).toList();
                    
                    // Count running agents (agents with RUNNING executions)
                    return agentExecutionRepository.findByStatus(ExecutionStatus.RUNNING.name())
                            .filter(execution -> agentIds.contains(execution.getAgentId()))
                            .map(execution -> execution.getAgentId())
                            .distinct()
                            .count()
                            .map(runningAgents -> Map.of(
                                    "teamId", teamId,
                                    "totalAgents", totalAgents,
                                    "runningAgents", runningAgents,
                                    "scheduledAgents", scheduledAgents,
                                    "utilizationPercentage", totalAgents > 0 ? (runningAgents * 100.0 / totalAgents) : 0.0
                            ));
                });
    }
    
    // ==================== EXECUTION MONITORING ====================
    
    @Override
    public Flux<AgentExecution> getFailedExecutions(String teamId, int limit) {
        return agentExecutionRepository.findByStatus(ExecutionStatus.FAILED.name())
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .take(limit);
    }
    
    @Override
    public Mono<Map<String, Object>> getWorkflowExecutionStatus(String workflowExecutionId, String teamId) {
        log.info("Getting workflow execution status {} for team {}", workflowExecutionId, teamId);
        
        return workflowExecutionService.getExecution(workflowExecutionId)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Workflow execution not found or access denied")))
                .map(execution -> {
                    // Calculate progress based on execution status
                    double progress = calculateExecutionProgress(execution);
                    
                    Map<String, Object> status = Map.of(
                            "workflowExecutionId", workflowExecutionId,
                            "workflowId", execution.getWorkflowId() != null ? execution.getWorkflowId() : "",
                            "status", execution.getStatus() != null ? execution.getStatus().toString() : "UNKNOWN",
                            "progress", progress,
                            "executedAt", execution.getExecutedAt(),
                            "executedBy", execution.getExecutedBy() != null ? execution.getExecutedBy() : "",
                            "durationMs", execution.getDurationMs() != null ? execution.getDurationMs() : 0L,
                            "teamId", execution.getTeamId()
                    );
                    
                    // Add agent context if this was triggered by an agent
                    if (execution.getAgentId() != null) {
                        return Map.of(
                                "workflowExecutionId", workflowExecutionId,
                                "workflowId", execution.getWorkflowId() != null ? execution.getWorkflowId() : "",
                                "status", execution.getStatus() != null ? execution.getStatus().toString() : "UNKNOWN",
                                "progress", progress,
                                "executedAt", execution.getExecutedAt(),
                                "executedBy", execution.getExecutedBy() != null ? execution.getExecutedBy() : "",
                                "durationMs", execution.getDurationMs() != null ? execution.getDurationMs() : 0L,
                                "teamId", execution.getTeamId(),
                                "agentContext", Map.of(
                                        "agentId", execution.getAgentId(),
                                        "agentName", execution.getAgentName() != null ? execution.getAgentName() : "",
                                        "agentTaskId", execution.getAgentTaskId() != null ? execution.getAgentTaskId() : "",
                                        "executionSource", execution.getExecutionSource() != null ? execution.getExecutionSource() : "",
                                        "agentExecutionId", execution.getAgentExecutionId() != null ? execution.getAgentExecutionId() : ""
                                )
                        );
                    }
                    
                    return status;
                })
                .onErrorReturn(Map.of(
                        "workflowExecutionId", workflowExecutionId,
                        "status", "NOT_FOUND",
                        "progress", 0.0,
                        "error", "Workflow execution not found or access denied"
                ));
    }
    
         /**
      * Calculate execution progress based on status
      */
     private double calculateExecutionProgress(org.lite.gateway.entity.LinqWorkflowExecution execution) {
         if (execution.getStatus() == null) {
             return 0.0;
         }
         
         return switch (execution.getStatus().toString()) {
             case "SUCCESS" -> 100.0;
             case "FAILED" -> 100.0; // Completed but failed
             case "IN_PROGRESS" -> 50.0; // Assume halfway if in progress
             default -> 25.0; // Unknown status, assume some progress
         };
     }
} 