package org.lite.gateway.controller;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.enums.AgentStatus;
import org.lite.gateway.service.AgentService;
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
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentController {
    
    private final AgentService agentService;
    private final AgentOrchestrationService agentOrchestrationService;
    
    // ==================== AGENT CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<Agent>> createAgent(@RequestBody Agent agent) {
        
        log.info("Creating agent '{}' for team {}", agent.getName(), agent.getTeamId());
        
        return agentService.createAgent(agent, agent.getTeamId(), agent.getCreatedBy())
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{agentId}")
    public Mono<ResponseEntity<Agent>> getAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting agent {} for team {}", agentId, teamId);
        
        return agentService.getAgentById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{agentId}")
    public Mono<ResponseEntity<Agent>> updateAgent(
            @PathVariable String agentId,
            @RequestBody Agent agentUpdates) {
        
        log.info("Updating agent {}", agentId);
        
        return agentService.updateAgent(agentId, agentUpdates)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @DeleteMapping("/{agentId}")
    public Mono<ResponseEntity<Boolean>> deleteAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Deleting agent {} for team {}", agentId, teamId);
        
        return agentService.deleteAgent(agentId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== AGENT LISTING & FILTERING ====================
    
    @GetMapping("/team/{teamId}")
    public Flux<Agent> getAgentsByTeam(@PathVariable String teamId) {
        log.info("Getting all agents for team {}", teamId);
        return agentService.getAgentsByTeam(teamId);
    }
    
    @GetMapping("/team/{teamId}/status/{status}")
    public Flux<Agent> getAgentsByTeamAndStatus(
            @PathVariable String teamId,
            @PathVariable AgentStatus status) {
        
        log.info("Getting agents with status {} for team {}", status, teamId);
        return agentService.getAgentsByTeamAndStatus(teamId, status);
    }
    
    @GetMapping("/team/{teamId}/ready")
    public Flux<Agent> getAgentsReadyToRun(@PathVariable String teamId) {
        log.info("Getting agents ready to run for team {}", teamId);
        return agentOrchestrationService.getAgentsReadyToRunByTeam(teamId);
    }
    
    // ==================== AGENT CONTROL OPERATIONS ====================
    
    @PostMapping("/{agentId}/enable")
    public Mono<ResponseEntity<Agent>> enableAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Enabling agent {} for team {}", agentId, teamId);
        
        return agentService.setAgentEnabled(agentId, teamId, true)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{agentId}/disable")
    public Mono<ResponseEntity<Agent>> disableAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Disabling agent {} for team {}", agentId, teamId);
        
        return agentService.setAgentEnabled(agentId, teamId, false)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{agentId}/schedule")
    public Mono<ResponseEntity<Agent>> scheduleAgent(
            @PathVariable String agentId,
            @RequestParam String cronExpression,
            @RequestParam String teamId) {
        
        log.info("Scheduling agent {} with cron: {} for team {}", agentId, cronExpression, teamId);
        
        return agentOrchestrationService.scheduleAgent(agentId, cronExpression, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{agentId}/unschedule")
    public Mono<ResponseEntity<Agent>> unscheduleAgent(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Unschedule agent {} for team {}", agentId, teamId);
        
        return agentOrchestrationService.unscheduleAgent(agentId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{agentId}/reset-error")
    public Mono<ResponseEntity<Agent>> resetAgentError(
            @PathVariable String agentId,
            @RequestParam String teamId,
            @RequestParam String resetBy) {
        
        log.info("Resetting error state for agent {} in team {}", agentId, teamId);
        
        return agentOrchestrationService.resetAgentError(agentId, teamId, resetBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== AGENT MONITORING ====================
    
    @GetMapping("/{agentId}/health")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentHealth(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Getting health status for agent {} in team {}", agentId, teamId);
        
        return agentOrchestrationService.getAgentHealth(agentId, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{agentId}/performance")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentPerformance(
            @PathVariable String agentId,
            @RequestParam String teamId,
            @RequestParam String from,
            @RequestParam String to) {
        
        log.info("Getting performance metrics for agent {} in team {} from {} to {}", 
                agentId, teamId, from, to);
        
        // TODO: Parse date strings to LocalDateTime
        return Mono.just(ResponseEntity.ok(Map.of("message", "Performance metrics endpoint - TODO: implement date parsing")));
    }
    
    @GetMapping("/team/{teamId}/health")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamAgentsHealth(@PathVariable String teamId) {
        log.info("Getting health summary for all agents in team {}", teamId);
        
        return agentOrchestrationService.getTeamAgentsHealth(teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/team/{teamId}/resource-usage")
    public Mono<ResponseEntity<Map<String, Object>>> getTeamResourceUsage(@PathVariable String teamId) {
        log.info("Getting resource usage for team {}", teamId);
        
        return agentOrchestrationService.getTeamResourceUsage(teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== BULK OPERATIONS ====================
    
    @PostMapping("/bulk/enable")
    public Mono<ResponseEntity<List<Agent>>> bulkEnableAgents(
            @RequestBody List<String> agentIds,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Bulk enabling {} agents for team {}", agentIds.size(), teamId);
        
        return agentOrchestrationService.bulkSetAgentsEnabled(agentIds, teamId, true, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/bulk/disable")
    public Mono<ResponseEntity<List<Agent>>> bulkDisableAgents(
            @RequestBody List<String> agentIds,
            @RequestParam String teamId,
            @RequestParam String updatedBy) {
        
        log.info("Bulk disabling {} agents for team {}", agentIds.size(), teamId);
        
        return agentOrchestrationService.bulkSetAgentsEnabled(agentIds, teamId, false, updatedBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/bulk/schedule")
    public Mono<ResponseEntity<List<Agent>>> bulkScheduleAgents(
            @RequestBody List<String> agentIds,
            @RequestParam String cronExpression,
            @RequestParam String teamId) {
        
        log.info("Bulk scheduling {} agents with cron: {} for team {}", agentIds.size(), cronExpression, teamId);
        
        return agentOrchestrationService.bulkScheduleAgents(agentIds, cronExpression, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/bulk/delete")
    public Mono<ResponseEntity<List<Boolean>>> bulkDeleteAgents(
            @RequestBody List<String> agentIds,
            @RequestParam String teamId) {
        
        log.info("Bulk deleting {} agents for team {}", agentIds.size(), teamId);
        
        return agentOrchestrationService.bulkDeleteAgents(agentIds, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== UTILITY OPERATIONS ====================
    
    @PostMapping("/{agentId}/validate")
    public Mono<ResponseEntity<Map<String, Object>>> validateAgentConfiguration(
            @PathVariable String agentId,
            @RequestParam String teamId) {
        
        log.info("Validating configuration for agent {} in team {}", agentId, teamId);
        
        return agentService.getAgentById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> agentOrchestrationService.validateAgentConfiguration(agent))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/team/{teamId}/capabilities")
    public Mono<ResponseEntity<Map<String, Object>>> getAgentCapabilitiesSummary(@PathVariable String teamId) {
        log.info("Getting capabilities summary for team {}", teamId);
        
        return agentOrchestrationService.getAgentCapabilitiesSummary(teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{agentId}/transfer")
    public Mono<ResponseEntity<Agent>> transferAgentOwnership(
            @PathVariable String agentId,
            @RequestParam String fromTeamId,
            @RequestParam String toTeamId,
            @RequestParam String transferredBy) {
        
        log.info("Transferring agent {} from team {} to team {}", agentId, fromTeamId, toTeamId);
        
        return agentOrchestrationService.transferAgentOwnership(agentId, fromTeamId, toTeamId, transferredBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
} 