package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.enums.AgentStatus;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.service.CronDescriptionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentServiceImpl implements AgentService {
    
    private final AgentRepository agentRepository;
    private final CronDescriptionService cronDescriptionService;
    
    @Override
    public Mono<Agent> createAgent(Agent agent, String teamId, String createdBy) {
        log.info("Creating agent '{}' for team {}", agent.getName(), teamId);
        
        agent.setTeamId(teamId);
        agent.setCreatedBy(createdBy);
        agent.setUpdatedBy(createdBy);
        agent.onCreate();
        
        if (agent.getCronExpression() != null && !agent.getCronExpression().trim().isEmpty()) {
            return Mono.just(cronDescriptionService.getCronDescription(agent.getCronExpression()))
                    .flatMap(description -> {
                        agent.setCronDescription(description);
                        log.info("Generated cron description: {}", description);
                        return agentRepository.save(agent);
                    })
                    .doOnSuccess(savedAgent -> log.info("Agent '{}' created successfully with ID: {}", savedAgent.getName(), savedAgent.getId()))
                    .doOnError(error -> log.error("Failed to create agent '{}': {}", agent.getName(), error.getMessage()));
        }
        
        return agentRepository.save(agent)
                .doOnSuccess(savedAgent -> log.info("Agent '{}' created successfully with ID: {}", savedAgent.getName(), savedAgent.getId()))
                .doOnError(error -> log.error("Failed to create agent '{}': {}", agent.getName(), error.getMessage()));
    }
    
    @Override
    public Mono<Agent> updateAgent(String agentId, Agent agentUpdates) {
        log.info("Updating agent {}", agentId);
        
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(existingAgent -> {
                    if (agentUpdates.getName() != null) existingAgent.setName(agentUpdates.getName());
                    if (agentUpdates.getDescription() != null) existingAgent.setDescription(agentUpdates.getDescription());
                    if (agentUpdates.getStatus() != null) existingAgent.setStatus(agentUpdates.getStatus());
                    if (agentUpdates.getRouteIdentifier() != null) existingAgent.setRouteIdentifier(agentUpdates.getRouteIdentifier());
                    if (agentUpdates.getPrimaryLinqToolId() != null) existingAgent.setPrimaryLinqToolId(agentUpdates.getPrimaryLinqToolId());
                    if (agentUpdates.getSupportedIntents() != null) existingAgent.setSupportedIntents(agentUpdates.getSupportedIntents());
                    if (agentUpdates.getCapabilities() != null) existingAgent.setCapabilities(agentUpdates.getCapabilities());
                    if (agentUpdates.getCronExpression() != null) existingAgent.setCronExpression(agentUpdates.getCronExpression());
                    if (agentUpdates.getMaxRetries() > 0) existingAgent.setMaxRetries(agentUpdates.getMaxRetries());
                    if (agentUpdates.getTimeoutMinutes() != null) existingAgent.setTimeoutMinutes(agentUpdates.getTimeoutMinutes());
                    
                    existingAgent.setUpdatedBy(agentUpdates.getUpdatedBy());
                    existingAgent.onUpdate();
                    
                    return agentRepository.save(existingAgent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} updated successfully", agentId))
                .doOnError(error -> log.error("Failed to update agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<Boolean> deleteAgent(String agentId, String teamId) {
        log.info("Deleting agent {} for team {}", agentId, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setEnabled(false);
                    agent.setStatus(AgentStatus.DISABLED);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    return agentRepository.save(agent).thenReturn(true);
                })
                .doOnSuccess(deleted -> log.info("Agent {} deleted successfully", agentId))
                .doOnError(error -> log.error("Failed to delete agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> setAgentEnabled(String agentId, String teamId, boolean enabled) {
        log.info("Setting agent {} enabled={} for team {}", agentId, enabled, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setEnabled(enabled);
                    agent.setStatus(enabled ? AgentStatus.IDLE : AgentStatus.DISABLED);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    return agentRepository.save(agent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} enabled={} successfully", agentId, enabled))
                .doOnError(error -> log.error("Failed to set agent {} enabled={}: {}", agentId, enabled, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> getAgentById(String agentId) {
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")));
    }
    
    @Override
    public Flux<Agent> getAgentsByTeam(String teamId) {
        return agentRepository.findByTeamId(teamId);
    }
    
    @Override
    public Flux<Agent> getAgentsByTeamAndStatus(String teamId, AgentStatus status) {
        return agentRepository.findByTeamIdAndStatus(teamId, status);
    }
}

