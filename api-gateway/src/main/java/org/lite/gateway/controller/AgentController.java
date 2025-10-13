package org.lite.gateway.controller;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentTaskService;
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

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentController {
    
    private final AgentService agentService;
    private final AgentAuthContextService agentAuthContextService;
    private final AgentTaskService agentTaskService;
    
    // ==================== AGENT CRUD OPERATIONS ====================
    
    @PostMapping
    public Mono<ResponseEntity<Object>> createAgent(
            @RequestBody Agent agent,
            ServerWebExchange exchange) {
        
        log.info("Creating agent '{}' for team {}", agent.getName(), agent.getTeamId());
        
        return agentAuthContextService.checkTeamAuthorization(agent.getTeamId(), exchange)
                .flatMap(authContext -> agentService.createAgent(agent, agent.getTeamId(), authContext.getUsername()))
                .map(createdAgent -> ResponseEntity.ok((Object) createdAgent))
                .onErrorResume(error -> {
                    log.warn("Authorization failed for createAgent: {}", error.getMessage());
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
    
    @GetMapping("/team/{teamId}/{agentId}")
    public Mono<ResponseEntity<Agent>> getAgent(
            @PathVariable String teamId,
            @PathVariable String agentId) {
        
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
    
    @GetMapping("/team/{teamId}/enabled")
    public Flux<Agent> getEnabledAgents(@PathVariable String teamId) {
        log.info("Getting enabled agents for team {}", teamId);
        return agentService.getAgentsByTeam(teamId)
                .filter(Agent::isEnabled);
    }
    
    @GetMapping("/{agentId}/tasks")
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
    
    @PostMapping("/{agentId}/transfer")
    public Mono<ResponseEntity<Agent>> transferAgentOwnership(
            @PathVariable String agentId,
            @RequestParam String fromTeamId,
            @RequestParam String toTeamId,
            @RequestParam String transferredBy) {
        
        log.info("Transferring agent {} from team {} to team {}", agentId, fromTeamId, toTeamId);
        
        return agentService.transferAgentOwnership(agentId, fromTeamId, toTeamId, transferredBy)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
} 