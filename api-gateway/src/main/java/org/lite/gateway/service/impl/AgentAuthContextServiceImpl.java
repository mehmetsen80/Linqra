package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.AgentAuthContextService;
import org.lite.gateway.service.AgentService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.UserService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Implementation of AgentAuthContextService for handling agent and task authorization.
 * 
 * This service provides centralized authorization logic, checking if users have proper
 * permissions (SUPER_ADMIN or team ADMIN) to perform operations on agents and tasks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentAuthContextServiceImpl implements AgentAuthContextService {
    
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;
    private final AgentService agentService;
    private final AgentTaskService agentTaskService;
    
    @Override
    public Mono<AgentAuthContext> checkAgentAuthorization(String agentId, ServerWebExchange exchange) {
        log.debug("Checking agent authorization for agentId: {}", agentId);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Agent-only operation: get agent directly
                    return agentService.getAgentById(agentId)
                            .flatMap(agent -> {
                                String teamId = agent.getTeamId();
                                
                                log.debug("Agent {} belongs to team {}", agentId, teamId);
                                
                                // Check authorization using helper method
                                return performAuthorizationCheck(user, agentId, null, teamId, "agent");
                            });
                })
                .doOnSuccess(authContext -> log.debug("Agent authorization successful for user {} on agent {}", 
                        authContext.getUsername(), agentId))
                .doOnError(error -> log.warn("Agent authorization failed for agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<AgentAuthContext> checkTaskAuthorization(String taskId, ServerWebExchange exchange) {
        log.debug("Checking task authorization for taskId: {}", taskId);
        
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> {
                    // Task-specific operation: get task first, then agent
                    return agentTaskService.getTaskById(taskId)
                            .flatMap(task -> {
                                String agentId = task.getAgentId();
                                
                                log.debug("Task {} belongs to agent {}", taskId, agentId);
                                
                                // Get the agent to get the teamId
                                return agentService.getAgentById(agentId)
                                        .flatMap(agent -> {
                                            String teamId = agent.getTeamId();
                                            
                                            log.debug("Agent {} belongs to team {}", agentId, teamId);
                                            
                                            // Check authorization using helper method
                                            return performAuthorizationCheck(user, agentId, taskId, teamId, "task");
                                        });
                            });
                })
                .doOnSuccess(authContext -> log.debug("Task authorization successful for user {} on task {}", 
                        authContext.getUsername(), taskId))
                .doOnError(error -> log.warn("Task authorization failed for task {}: {}", taskId, error.getMessage()));
    }
    
    /**
     * Helper method to perform the common authorization check logic.
     * 
     * @param user The authenticated user
     * @param agentId The agent ID
     * @param taskId The task ID (null for agent-only operations)
     * @param teamId The team ID
     * @param resourceType "agent" or "task" for logging purposes
     * @return Mono containing AgentAuthContext if authorized, or error if not authorized
     */
    private Mono<AgentAuthContext> performAuthorizationCheck(User user, String agentId, String taskId, String teamId, String resourceType) {
        // Check authorization: SUPER_ADMIN or team ADMIN
        if (user.getRoles().contains("SUPER_ADMIN")) {
            log.debug("User {} authorized as SUPER_ADMIN for {} {}", 
                    user.getUsername(), resourceType, 
                    taskId != null ? taskId : agentId);
            
            if (taskId != null) {
                return Mono.just(new AgentAuthContext(agentId, taskId, teamId, user.getUsername(), true));
            } else {
                return Mono.just(new AgentAuthContext(agentId, teamId, user.getUsername(), true));
            }
        }
        
        // For non-SUPER_ADMIN users, check team role
        return teamService.hasRole(teamId, user.getId(), "ADMIN")
                .map(isAdmin -> {
                    if (!isAdmin) {
                        log.warn("User {} denied access to {} {} - not a team admin", 
                                user.getUsername(), resourceType, 
                                taskId != null ? taskId : agentId);
                        throw new RuntimeException("Only team administrators can perform this operation");
                    }
                    
                    log.debug("User {} authorized as team ADMIN for {} {} in team {}", 
                            user.getUsername(), resourceType, 
                            taskId != null ? taskId : agentId, teamId);
                    
                    if (taskId != null) {
                        return new AgentAuthContext(agentId, taskId, teamId, user.getUsername(), false);
                    } else {
                        return new AgentAuthContext(agentId, teamId, user.getUsername(), false);
                    }
                });
    }
    
    @Override
    public Mono<AgentAuthContext> checkTeamAuthorization(String teamId, ServerWebExchange exchange) {
        log.debug("Checking team authorization for teamId: {}", teamId);
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> performTeamAuthorizationCheck(user, teamId))
                .doOnSuccess(authContext -> log.debug("Team authorization successful for user {} on team {}",
                        authContext.getUsername(), teamId))
                .doOnError(error -> log.warn("Team authorization failed for team {}: {}", teamId, error.getMessage()));
    }
    
    private Mono<AgentAuthContext> performTeamAuthorizationCheck(User user, String teamId) {
        if (user.getRoles().contains("SUPER_ADMIN")) {
            log.debug("User {} authorized as SUPER_ADMIN for team {}", user.getUsername(), teamId);
            return Mono.just(new AgentAuthContext(null, teamId, user.getUsername(), true));
        }
        return teamService.hasRole(teamId, user.getId(), "ADMIN")
                .map(isAdmin -> {
                    if (!isAdmin) {
                        log.warn("User {} denied access to team {} - not a team admin", user.getUsername(), teamId);
                        throw new RuntimeException("Only team administrators can perform this operation");
                    }
                    log.debug("User {} authorized as team ADMIN for team {}", user.getUsername(), teamId);
                    return new AgentAuthContext(null, teamId, user.getUsername(), false);
                });
    }
} 