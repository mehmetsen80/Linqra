package org.lite.gateway.controller;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
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
    public Mono<ResponseEntity<Map<String, Object>>> getAgentHealth(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting health status for agent {} in team {}", agentId, teamId);
        
        return agentMonitoringService.getAgentHealth(agentId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/team/{teamId}/health")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamAgentsHealth(@PathVariable String teamId) {
        log.info("Getting health summary for all agents in team {}", teamId);
        
        return agentMonitoringService.getTeamAgentsHealth(teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
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
            @RequestParam String teamId,
            @RequestParam String from,
            @RequestParam String to) {
        
        log.info("Getting performance metrics for agent {} in team {} from {} to {}", 
                agentId, teamId, from, to);
        
        try {
            // Parse date strings to LocalDateTime
            LocalDateTime fromDate = LocalDateTime.parse(from);
            LocalDateTime toDate = LocalDateTime.parse(to);
            
            return agentMonitoringService.getAgentPerformance(agentId, teamId, fromDate, toDate)
                    .map(ResponseEntity::ok)
                    .onErrorReturn(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error parsing date parameters: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid date format. Use ISO format: yyyy-MM-ddTHH:mm:ss")));
        }
    }
    
    @GetMapping("/team/{teamId}/execution-stats")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamExecutionStats(
            @PathVariable String teamId,
            @RequestParam String from,
            @RequestParam String to) {
        
        log.info("Getting execution statistics for team {} from {} to {}", teamId, from, to);
        
        try {
            LocalDateTime fromDate = LocalDateTime.parse(from);
            LocalDateTime toDate = LocalDateTime.parse(to);
            
            return agentMonitoringService.getTeamExecutionStats(teamId, fromDate, toDate)
                    .map(ResponseEntity::ok)
                    .onErrorReturn(ResponseEntity.badRequest().build());
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
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/team/{teamId}/capabilities")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentCapabilitiesSummary(@PathVariable String teamId) {
        log.info("Getting capabilities summary for team {}", teamId);
        
        return agentMonitoringService.getAgentCapabilitiesSummary(teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
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
                        "completedAt", execution.getCompletedAt()
                ));
    }
    
    @GetMapping("/workflow/{workflowExecutionId}/status")
    public Mono<ResponseEntity<Map<String, Object>>> getWorkflowExecutionStatus(
            @PathVariable String workflowExecutionId,
            @RequestParam String teamId) {
        
        log.info("Getting workflow execution status {} for team {}", workflowExecutionId, teamId);
        
        return agentMonitoringService.getWorkflowExecutionStatus(workflowExecutionId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
} 