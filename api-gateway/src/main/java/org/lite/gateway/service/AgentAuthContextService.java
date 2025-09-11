package org.lite.gateway.service;

import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

/**
 * Service interface for handling agent and task authorization.
 * 
 * This service provides centralized authorization logic for agent and task operations,
 * ensuring users have proper permissions (SUPER_ADMIN or team ADMIN) to perform operations.
 */
public interface AgentAuthContextService {
    
    /**
     * Check authorization for agent-specific operations.
     * 
     * @param agentId The agent ID to authorize against
     * @param exchange The ServerWebExchange containing user context
     * @return Mono containing AgentAuthContext if authorized, or error if not authorized
     */
    Mono<AgentAuthContext> checkAgentAuthorization(String agentId, ServerWebExchange exchange);
    
    /**
     * Check authorization for task-specific operations.
     * 
     * @param taskId The task ID to authorize against
     * @param exchange The ServerWebExchange containing user context
     * @return Mono containing AgentAuthContext if authorized, or error if not authorized
     */
    Mono<AgentAuthContext> checkTaskAuthorization(String taskId, ServerWebExchange exchange);
    
    /**
     * Check authorization for team-level operations (like creating agents).
     * 
     * @param teamId The team ID to authorize against
     * @param exchange The ServerWebExchange containing user context
     * @return Mono containing AgentAuthContext if authorized, or error if not authorized
     */
    Mono<AgentAuthContext> checkTeamAuthorization(String teamId, ServerWebExchange exchange);
    
    /**
     * Context object containing authorization information for agent and task operations
     */
    class AgentAuthContext {
        private final String agentId;
        private final String taskId;
        private final String teamId;
        private final String username;
        private final boolean isSuperAdmin;
        
        // Constructor for agent-only operations (createTask, getTasksByAgent)
        public AgentAuthContext(String agentId, String teamId, String username, boolean isSuperAdmin) {
            this.agentId = agentId;
            this.taskId = null;
            this.teamId = teamId;
            this.username = username;
            this.isSuperAdmin = isSuperAdmin;
        }
        
        // Constructor for task-specific operations (getTask, updateTask, deleteTask, etc.)
        public AgentAuthContext(String agentId, String taskId, String teamId, String username, boolean isSuperAdmin) {
            this.agentId = agentId;
            this.taskId = taskId;
            this.teamId = teamId;
            this.username = username;
            this.isSuperAdmin = isSuperAdmin;
        }
        
        public String getAgentId() { return agentId; }
        public String getTaskId() { return taskId; }
        public String getTeamId() { return teamId; }
        public String getUsername() { return username; }
        public boolean isSuperAdmin() { return isSuperAdmin; }
        
        // Helper method to check if this context includes task information
        public boolean hasTaskId() { return taskId != null; }
    }
} 