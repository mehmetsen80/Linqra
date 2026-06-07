package org.lite.gateway.controller;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.dto.ErrorResponse;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.dto.AgentDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
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
        private final AgentAuthContextService agentAuthContextService;
        private final AgentTaskService agentTaskService;
        private final TeamContextService teamContextService;

        // ==================== AGENT CRUD OPERATIONS ====================

        @PostMapping
        public Mono<ResponseEntity<Object>> createAgent(
                        @RequestBody Agent agent,
                        ServerWebExchange exchange) {

                log.info("Creating agent '{}'", agent.getName());

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                        log.info("Creating agent '{}' for team {}", agent.getName(), teamId);
                                        agent.setTeamId(teamId); // Set teamId from context

                                        return agentAuthContextService.checkTeamAuthorization(teamId, exchange)
                                                        .flatMap(authContext -> agentService.createAgent(agent, teamId,
                                                                        authContext.getUsername()));
                                })
                                .map(createdAgent -> ResponseEntity.ok((Object) createdAgent))
                                .onErrorResume(error -> {
                                        log.warn("Failed to create agent: {}", error.getMessage());
                                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                                        ErrorCode.FORBIDDEN,
                                                        error.getMessage(),
                                                        HttpStatus.FORBIDDEN.value());
                                        return Mono.just(ResponseEntity
                                                        .status(HttpStatus.FORBIDDEN)
                                                        .body((Object) errorResponse));
                                });
        }

        @GetMapping("/team/{teamId}/{agentId}")
        public Mono<ResponseEntity<AgentDTO>> getAgent(
                        @PathVariable String teamId,
                        @PathVariable String agentId) {

                log.info("Getting agent {} for team {}", agentId, teamId);

                return agentService.getAgentById(agentId)
                                .filter(agent -> teamId.equals(agent.getTeamId()))
                                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                                .flatMap(agent -> agentTaskService.getTasksByAgent(agent.getId(), teamId)
                                                .collectList()
                                                .map(tasks -> AgentDTO.fromEntity(agent, tasks)))
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
        public Mono<ResponseEntity<Object>> deleteAgent(
                        @PathVariable String agentId,
                        ServerWebExchange exchange) {

                log.info("Deleting agent {}", agentId);

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                        log.info("Deleting agent {} for team {}", agentId, teamId);

                                        return agentAuthContextService.checkAgentAuthorization(agentId, exchange)
                                                        .flatMap(authContext -> {
                                                                // First, check if there are any tasks for this agent
                                                                return agentTaskService.getTasksByAgent(agentId, teamId)
                                                                                .collectList()
                                                                                .flatMap(tasks -> {
                                                                                        if (!tasks.isEmpty()) {
                                                                                                log.warn("Cannot delete agent {} - has {} tasks",
                                                                                                                agentId,
                                                                                                                tasks.size());
                                                                                                ErrorResponse errorResponse = ErrorResponse
                                                                                                                .fromErrorCode(
                                                                                                                                ErrorCode.VALIDATION_ERROR,
                                                                                                                                String.format("Cannot delete agent. Please delete all %d task(s) first.",
                                                                                                                                                tasks.size()),
                                                                                                                                HttpStatus.BAD_REQUEST
                                                                                                                                                .value());
                                                                                                return Mono.just(
                                                                                                                ResponseEntity
                                                                                                                                .status(HttpStatus.BAD_REQUEST)
                                                                                                                                .body((Object) errorResponse));
                                                                                        }

                                                                                        // No tasks, proceed with
                                                                                        // deletion
                                                                                        return agentService.deleteAgent(
                                                                                                        agentId, teamId)
                                                                                                        .map(deleted -> ResponseEntity
                                                                                                                        .ok((Object) Map.of(
                                                                                                                                        "success",
                                                                                                                                        true,
                                                                                                                                        "message",
                                                                                                                                        "Agent deleted successfully")));
                                                                                });
                                                        });
                                })
                                .onErrorResume(error -> {
                                        log.error("Failed to delete agent {}: {}", agentId, error.getMessage());
                                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                                        ErrorCode.INTERNAL_ERROR,
                                                        "Failed to delete agent: " + error.getMessage(),
                                                        HttpStatus.INTERNAL_SERVER_ERROR.value());
                                        return Mono.just(ResponseEntity
                                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body((Object) errorResponse));
                                });
        }

        // ==================== AGENT LISTING & FILTERING ====================

        @GetMapping("/team/{teamId}")
        public Flux<AgentDTO> getAgentsByTeam(@PathVariable String teamId) {
                log.info("Getting all agents for team {}", teamId);
                return agentService.getAgentsByTeam(teamId)
                                .flatMap(agent -> agentTaskService.getTasksByAgent(agent.getId(), teamId)
                                                .collectList()
                                                .map(tasks -> AgentDTO.fromEntity(agent, tasks)));
        }

        @GetMapping("/team/{teamId}/enabled")
        public Flux<AgentDTO> getEnabledAgents(@PathVariable String teamId) {
                log.info("Getting enabled agents for team {}", teamId);
                return agentService.getAgentsByTeam(teamId)
                                .filter(Agent::isEnabled)
                                .flatMap(agent -> agentTaskService.getTasksByAgent(agent.getId(), teamId)
                                                .collectList()
                                                .map(tasks -> AgentDTO.fromEntity(agent, tasks)));
        }

        @GetMapping("/{agentId}/tasks")
        public Mono<ResponseEntity<Object>> getTasksByAgent(
                        @PathVariable String agentId,
                        ServerWebExchange exchange) {

                log.info("Getting all tasks for agent {}", agentId);

                return agentAuthContextService.checkAgentAuthorization(agentId, exchange)
                                .map(authContext -> ResponseEntity.ok((Object) agentTaskService.getTasksByAgent(agentId,
                                                authContext.getTeamId())))
                                .onErrorResume(error -> {
                                        log.warn("Authorization failed for getTasksByAgent {}: {}", agentId,
                                                        error.getMessage());
                                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                                        ErrorCode.FORBIDDEN,
                                                        error.getMessage(),
                                                        HttpStatus.FORBIDDEN.value());
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

        @PostMapping("/list")
        public Mono<ResponseEntity<Object>> getAgentsByIds(
                        @RequestBody List<String> agentIds,
                        ServerWebExchange exchange) {

                log.info("Batch retrieving agents by ID list: {}", agentIds);

                return teamContextService.getAllAuthorizedTeams(exchange)
                                .flatMapMany(authorizedTeams -> {
                                        boolean hasBypass = authorizedTeams.contains("ALL_TEAMS_BYPASS");
                                        return agentService.getAgentsByIds(agentIds)
                                                        .filter(agent -> hasBypass || authorizedTeams.contains(agent.getTeamId()))
                                                        .flatMap(agent -> agentTaskService
                                                                        .getTasksByAgent(agent.getId(), agent.getTeamId())
                                                                        .collectList()
                                                                        .map(tasks -> AgentDTO.fromEntity(agent, tasks)));
                                })
                                .collectList()
                                .map(agents -> ResponseEntity.ok((Object) agents))
                                .onErrorResume(error -> {
                                        log.error("Failed to batch retrieve agents: {}", error.getMessage());
                                        ErrorResponse errorResponse = ErrorResponse.fromErrorCode(
                                                        ErrorCode.INTERNAL_ERROR,
                                                        "Failed to batch retrieve agents: " + error.getMessage(),
                                                        HttpStatus.INTERNAL_SERVER_ERROR.value());
                                        return Mono.just(ResponseEntity
                                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body((Object) errorResponse));
                                });
        }
}