package org.lite.gateway.service;

import org.lite.gateway.entity.Agent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentService {
    
    /**
     * Create a new agent
     */
    Mono<Agent> createAgent(Agent agent, String teamId, String createdBy);
    
    /**
     * Update an existing agent
     */
    Mono<Agent> updateAgent(String agentId, Agent agentUpdates);
    
    /**
     * Delete an agent (soft delete by setting enabled=false)
     */
    Mono<Boolean> deleteAgent(String agentId, String teamId);
    
    /**
     * Set agent enabled/disabled status
     */
    Mono<Agent> setAgentEnabled(String agentId, String teamId, boolean enabled);
    
    /**
     * Get agent by ID
     */
    Mono<Agent> getAgentById(String agentId);
    
    /**
     * Get all agents for a team
     */
    Flux<Agent> getAgentsByTeam(String teamId);
    
    /**
     * Transfer agent ownership to another team
     */
    Mono<Agent> transferAgentOwnership(String agentId, String fromTeamId, String toTeamId, String transferredBy);

}
