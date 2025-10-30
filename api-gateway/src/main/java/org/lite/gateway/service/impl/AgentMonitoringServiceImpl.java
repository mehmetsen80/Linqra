package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;

import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.TeamRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentMonitoringServiceImpl implements AgentMonitoringService {
    
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final TeamRepository teamRepository;
    private final LinqWorkflowExecutionService workflowExecutionService;
    
    // ==================== HEALTH MONITORING ====================
    
    @Override
    public Mono<Map<String, Object>> getAgentHealth(String agentId) {
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(agent -> {
                    // Get latest execution for last run and error info
                    Mono<AgentExecution> latestExecutionMono = agentExecutionRepository
                            .findByAgentIdOrderByCreatedAtDesc(agentId)
                            .next();
                    
                    // Get next scheduled task run time
                    Mono<LocalDateTime> nextRunMono = agentTaskRepository
                            .findTasksReadyToRunByAgent(agentId, LocalDateTime.now().plusYears(1)) // future tasks
                            .map(AgentTask::getNextRun)
                            .filter(nextRun -> nextRun != null && nextRun.isAfter(LocalDateTime.now()))
                            .sort()
                            .next();
                    
                    // Combine both monos, handling empty cases
                    return Mono.zip(
                            latestExecutionMono.defaultIfEmpty(new AgentExecution()),
                            nextRunMono.map(Optional::of).defaultIfEmpty(Optional.empty())
                    ).map(tuple -> {
                        AgentExecution latestExecution = tuple.getT1();
                        Optional<LocalDateTime> nextRunOpt = tuple.getT2();
                        
                        // Use HashMap to allow null values (Map.of() doesn't allow nulls)
                        Map<String, Object> health = new java.util.HashMap<>();
                        health.put("teamId", agent.getTeamId());
                        health.put("agentId", agentId);
                        health.put("enabled", agent.isEnabled());
                        health.put("lastRun", latestExecution.getStartedAt());
                        health.put("nextRun", nextRunOpt.orElse(null));
                        health.put("lastError", latestExecution.getErrorMessage());
                        health.put("canExecute", agent.canExecute());
                        
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
    public Mono<Map<String, Object>> getAgentPerformance(String agentId, LocalDateTime from, LocalDateTime to) {
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(agent -> 
                    agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to)
                        .collectList()
                        .map(executions -> {
                            long totalExecutions = executions.size();
                            
                            // Count by result
                            long successfulExecutions = executions.stream()
                                    .filter(e -> ExecutionResult.SUCCESS.equals(e.getResult()))
                                    .count();
                            long failedExecutions = executions.stream()
                                    .filter(e -> ExecutionResult.FAILURE.equals(e.getResult()))
                                    .count();
                            long partialSuccessExecutions = executions.stream()
                                    .filter(e -> ExecutionResult.PARTIAL_SUCCESS.equals(e.getResult()))
                                    .count();
                            long skippedExecutions = executions.stream()
                                    .filter(e -> ExecutionResult.SKIPPED.equals(e.getResult()))
                                    .count();
                            long unknownExecutions = executions.stream()
                                    .filter(e -> ExecutionResult.UNKNOWN.equals(e.getResult()))
                                    .count();
                            
                            // Count by status
                            long runningExecutions = executions.stream()
                                    .filter(e -> ExecutionStatus.RUNNING.equals(e.getStatus()))
                                    .count();
                            long completedExecutions = executions.stream()
                                    .filter(e -> ExecutionStatus.COMPLETED.equals(e.getStatus()))
                                    .count();
                            long failedStatusExecutions = executions.stream()
                                    .filter(e -> ExecutionStatus.FAILED.equals(e.getStatus()))
                                    .count();
                            long cancelledExecutions = executions.stream()
                                    .filter(e -> ExecutionStatus.CANCELLED.equals(e.getStatus()))
                                    .count();
                            long timeoutExecutions = executions.stream()
                                    .filter(e -> ExecutionStatus.TIMEOUT.equals(e.getStatus()))
                                    .count();
                            
                            double avgExecutionTime = executions.stream()
                                    .filter(e -> e.getExecutionDurationMs() != null)
                                    .mapToLong(AgentExecution::getExecutionDurationMs)
                                    .average()
                                    .orElse(0.0);
                            
                            return Map.ofEntries(
                                    Map.entry("teamId", agent.getTeamId()),
                                    Map.entry("agentId", agentId),
                                    Map.entry("totalExecutions", totalExecutions),
                                    Map.entry("successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0),
                                    Map.entry("averageExecutionTimeMs", avgExecutionTime),
                                    Map.entry("resultBreakdown", Map.of(
                                            "successful", successfulExecutions,
                                            "failed", failedExecutions,
                                            "partialSuccess", partialSuccessExecutions,
                                            "skipped", skippedExecutions,
                                            "unknown", unknownExecutions
                                    )),
                                    Map.entry("statusBreakdown", Map.of(
                                            "running", runningExecutions,
                                            "completed", completedExecutions,
                                            "failed", failedStatusExecutions,
                                            "cancelled", cancelledExecutions,
                                            "timeout", timeoutExecutions
                                    ))
                            );
                        })
                );
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to) {
        return getTeamExecutionStats(teamId, from, to, null);
    }
    
    public Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to, String specificAgentId) {
        // First get all agents for the team, then get their executions
        return agentRepository.findByTeamId(teamId)
                .map(Agent::getId)
                .collectList()
                .flatMap(agentIds -> {
                    if (agentIds.isEmpty()) {
                        return teamRepository.findById(teamId)
                                .flatMap(team -> Mono.<Map<String, Object>>error(new RuntimeException("No agents assigned to team: " + team.getName())))
                                .switchIfEmpty(Mono.<Map<String, Object>>error(new RuntimeException("No agents assigned to team: " + teamId)));
                    }
                    
                    // Filter to specific agent if provided
                    List<String> targetAgentIds = specificAgentId != null 
                        ? (agentIds.contains(specificAgentId) ? List.of(specificAgentId) : List.of())
                        : agentIds;
                    
                    if (targetAgentIds.isEmpty()) {
                        return teamRepository.findById(teamId)
                                .flatMap(team -> Mono.<Map<String, Object>>error(new RuntimeException("Agent not found in team: " + team.getName())))
                                .switchIfEmpty(Mono.<Map<String, Object>>error(new RuntimeException("Agent not found in team: " + teamId)));
                    }
                    
                    return Flux.fromIterable(targetAgentIds)
                            .flatMap(agentId -> agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to))
                            .collectList()
                            .map(executions -> {
                                long totalExecutions = executions.size();
                                
                                // Count by result
                                long successfulExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.SUCCESS.equals(e.getResult()))
                                        .count();
                                long failedExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.FAILURE.equals(e.getResult()))
                                        .count();
                                long partialSuccessExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.PARTIAL_SUCCESS.equals(e.getResult()))
                                        .count();
                                long skippedExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.SKIPPED.equals(e.getResult()))
                                        .count();
                                long unknownExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.UNKNOWN.equals(e.getResult()))
                                        .count();
                                
                                // Count by status
                                long runningExecutions = executions.stream()
                                        .filter(e -> ExecutionStatus.RUNNING.equals(e.getStatus()))
                                        .count();
                                long completedExecutions = executions.stream()
                                        .filter(e -> ExecutionStatus.COMPLETED.equals(e.getStatus()))
                                        .count();
                                long failedStatusExecutions = executions.stream()
                                        .filter(e -> ExecutionStatus.FAILED.equals(e.getStatus()))
                                        .count();
                                long cancelledExecutions = executions.stream()
                                        .filter(e -> ExecutionStatus.CANCELLED.equals(e.getStatus()))
                                        .count();
                                long timeoutExecutions = executions.stream()
                                        .filter(e -> ExecutionStatus.TIMEOUT.equals(e.getStatus()))
                                        .count();
                                
                                // Calculate average execution time (only for executions with duration)
                                double averageExecutionTimeMs = executions.stream()
                                        .filter(e -> e.getExecutionDurationMs() != null && e.getExecutionDurationMs() > 0)
                                        .mapToLong(e -> e.getExecutionDurationMs())
                                        .average()
                                        .orElse(0.0);
                                
                                // Calculate hourly execution distribution
                                Map<String, Integer> hourlyExecutions = new HashMap<>();
                                for (AgentExecution execution : executions) {
                                    if (execution.getStartedAt() != null) {
                                        int hour = execution.getStartedAt().getHour();
                                        hourlyExecutions.merge(String.valueOf(hour), 1, Integer::sum);
                                    }
                                }

                                return Map.ofEntries(
                                        Map.entry("teamId", teamId),
                                        Map.entry("totalExecutions", totalExecutions),
                                        Map.entry("successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0),
                                        Map.entry("averageExecutionTimeMs", averageExecutionTimeMs),
                                        Map.entry("period", Map.of("from", from, "to", to)),
                                        Map.entry("resultBreakdown", Map.of(
                                                "successful", successfulExecutions,
                                                "failed", failedExecutions,
                                                "partialSuccess", partialSuccessExecutions,
                                                "skipped", skippedExecutions,
                                                "unknown", unknownExecutions
                                        )),
                                        Map.entry("statusBreakdown", Map.of(
                                                "running", runningExecutions,
                                                "completed", completedExecutions,
                                                "failed", failedStatusExecutions,
                                                "cancelled", cancelledExecutions,
                                                "timeout", timeoutExecutions
                                        )),
                                        Map.entry("hourlyExecutions", hourlyExecutions)
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
                    
                    if (agents.isEmpty()) {
                        return Mono.just(Map.of(
                                "teamId", teamId,
                                "totalAgents", 0L,
                                "runningAgents", 0L,
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
    public Mono<Map<String, Object>> getWorkflowExecutionStatus(String workflowExecutionId) {
        log.info("Getting workflow execution status {}", workflowExecutionId);
        
        return workflowExecutionService.getExecution(workflowExecutionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Workflow execution not found")))
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