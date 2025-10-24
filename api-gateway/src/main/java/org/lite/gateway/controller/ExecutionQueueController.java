package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ExecutionQueue;
import org.lite.gateway.service.ExecutionQueueService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/execution-queue")
@RequiredArgsConstructor
@Slf4j
public class ExecutionQueueController {
    
    private final ExecutionQueueService executionQueueService;
    private final TeamContextService teamContextService;
    
    /**
     * Get the execution queue for the current team
     */
    @GetMapping
    public Flux<ExecutionQueue> getQueue(ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> {
                log.info("📋 Getting execution queue for team: {}", teamId);
                return executionQueueService.getQueueForTeam(teamId);
            });
    }
    
    /**
     * Add an execution to the queue
     */
    @PostMapping
    public Mono<ExecutionQueue> addToQueue(@RequestBody AddToQueueRequest request, ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> {
                log.info("📋 Adding execution to queue: {} for team: {}", request.getExecutionId(), teamId);
                return executionQueueService.addToQueue(
                    request.getExecutionId(),
                    request.getAgentId(),
                    request.getAgentName(),
                    request.getTaskId(),
                    request.getTaskName(),
                    teamId,
                    request.getUserId()
                );
            });
    }
    
    /**
     * Remove an execution from the queue
     */
    @DeleteMapping("/{executionId}")
    public Mono<Void> removeFromQueue(@PathVariable String executionId, ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> {
                log.info("📋 Removing execution from queue: {} for team: {}", executionId, teamId);
                return executionQueueService.removeFromQueue(executionId);
            });
    }
    
    /**
     * Get the next execution to start
     */
    @GetMapping("/next")
    public Mono<ExecutionQueue> getNextExecution(ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> {
                log.info("📋 Getting next execution for team: {}", teamId);
                return executionQueueService.getNextExecution(teamId);
            });
    }
    
    // Request DTO
    public static class AddToQueueRequest {
        private String executionId;
        private String agentId;
        private String agentName;
        private String taskId;
        private String taskName;
        private String userId;
        
        // Getters and setters
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getTaskName() { return taskName; }
        public void setTaskName(String taskName) { this.taskName = taskName; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
