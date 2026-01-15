package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.service.AgentService;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;

    @Override
    public Mono<Agent> createAgent(Agent agent, String teamId, String createdBy) {
        log.info("Creating agent '{}' for team {}", agent.getName(), teamId);

        agent.setTeamId(teamId);
        agent.setCreatedBy(createdBy);
        agent.setUpdatedBy(createdBy);

        return agentRepository.save(agent)
                .doOnSuccess(savedAgent -> log.info("Agent '{}' created successfully with ID: {}", savedAgent.getName(),
                        savedAgent.getId()))
                .doOnError(error -> log.error("Failed to create agent '{}': {}", agent.getName(), error.getMessage()));
    }

    @Override
    public Mono<Agent> updateAgent(String agentId, Agent agentUpdates) {
        log.info("Updating agent {}", agentId);

        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(existingAgent -> {
                    if (agentUpdates.getName() != null)
                        existingAgent.setName(agentUpdates.getName());
                    if (agentUpdates.getDescription() != null)
                        existingAgent.setDescription(agentUpdates.getDescription());
                    if (agentUpdates.getSupportedIntents() != null)
                        existingAgent.setSupportedIntents(agentUpdates.getSupportedIntents());
                    if (agentUpdates.getCapabilities() != null)
                        existingAgent.setCapabilities(agentUpdates.getCapabilities());

                    existingAgent.setUpdatedBy(agentUpdates.getUpdatedBy());

                    return agentRepository.save(existingAgent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} updated successfully", agentId))
                .doOnError(error -> log.error("Failed to update agent {}: {}", agentId, error.getMessage()));
    }

    @Override
    public Mono<Boolean> deleteAgent(String agentId, String teamId) {
        log.info("Hard deleting agent {} for team {}", agentId, teamId);

        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    // Hard delete - actually remove from database
                    return agentRepository.deleteById(agentId).thenReturn(true);
                })
                .doOnSuccess(deleted -> log.info("Agent {} hard deleted successfully", agentId))
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
                    agent.setUpdatedBy("system");
                    return agentRepository.save(agent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} enabled={} successfully", agentId, enabled))
                .doOnError(error -> log.error("Failed to set agent {} enabled={}: {}", agentId, enabled,
                        error.getMessage()));
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
    public Mono<Agent> getAgentByIdAndTeam(String agentId, String teamId) {
        return agentRepository.findByIdAndTeamId(agentId, teamId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")));
    }

    @Override
    public Mono<Agent> transferAgentOwnership(String agentId, String fromTeamId, String toTeamId,
            String transferredBy) {
        log.info("Transferring agent {} from team {} to team {}", agentId, fromTeamId, toTeamId);

        return agentRepository.findById(agentId)
                .filter(agent -> fromTeamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setTeamId(toTeamId);
                    agent.setUpdatedBy(transferredBy);
                    return agentRepository.save(agent);
                })
                .doOnSuccess(transferredAgent -> log.info("Agent {} ownership transferred successfully", agentId))
                .doOnError(
                        error -> log.error("Failed to transfer agent {} ownership: {}", agentId, error.getMessage()));
    }
}
