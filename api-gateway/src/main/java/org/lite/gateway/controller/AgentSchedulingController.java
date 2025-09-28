package org.lite.gateway.controller;

import org.lite.gateway.dto.AgentSchedulingRequest;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.service.AgentSchedulingService;
import org.lite.gateway.service.AgentAuthContextService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-tasks/scheduling")
@RequiredArgsConstructor
@Slf4j
public class AgentSchedulingController {
    
    private final AgentSchedulingService agentSchedulingService;
    private final AgentAuthContextService agentAuthContextService;
    
    // ==================== TASK SCHEDULING OPERATIONS ====================
    
    @PostMapping("/{taskId}/schedule")
    public Mono<ResponseEntity<AgentTask>> scheduleTask(
            @PathVariable String taskId,
            @RequestBody AgentSchedulingRequest request,
            ServerWebExchange exchange) {
        
        log.info("Scheduling task {} with cron via body for authorized agent", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(auth -> agentSchedulingService.scheduleTask(taskId, request.getCronExpression(), auth.getTeamId()))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/unschedule")
    public Mono<ResponseEntity<AgentTask>> unscheduleTask(
            @PathVariable String taskId,
            ServerWebExchange exchange) {
        
        log.info("Unscheduling task {} for authorized agent", taskId);
        
        return agentAuthContextService.checkTaskAuthorization(taskId, exchange)
                .flatMap(auth -> agentSchedulingService.unscheduleTask(taskId, auth.getTeamId()))
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    // ==================== SCHEDULING QUERIES ====================
    
    @GetMapping("/ready-to-run")
    public Flux<AgentTask> getTasksReadyToRun() {
        log.info("Getting all tasks ready to run");
        return agentSchedulingService.getTasksReadyToRun();
    }
    
    @GetMapping("/agent/{agentId}/ready-to-run")
    public Flux<AgentTask> getTasksReadyToRunByAgent(@PathVariable String agentId) {
        log.info("Getting tasks ready to run for agent {}", agentId);
        return agentSchedulingService.getTasksReadyToRunByAgent(agentId);
    }
    
    @GetMapping("/team/{teamId}/ready-to-run")
    public Flux<AgentTask> getTasksReadyToRunByTeam(@PathVariable String teamId) {
        log.info("Getting tasks ready to run for team {}", teamId);
        return agentSchedulingService.getTasksReadyToRunByTeam(teamId);
    }
    
    // ==================== SCHEDULING MANAGEMENT ====================
    
    @PostMapping("/{taskId}/next-run")
    public Mono<ResponseEntity<AgentTask>> updateTaskNextRunTime(
            @PathVariable String taskId,
            @RequestBody Map<String, String> request) {
        
        try {
            LocalDateTime nextRun = LocalDateTime.parse(request.get("nextRun"));
            log.info("Updating next run time for task {} to {}", taskId, nextRun);
            
            return agentSchedulingService.updateTaskNextRunTime(taskId, nextRun)
                    .map(ResponseEntity::ok)
                    .onErrorReturn(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error parsing nextRun date: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(null)); // Could return error details here
        }
    }
    
    @PostMapping("/{taskId}/last-run")
    public Mono<ResponseEntity<AgentTask>> updateTaskLastRunTime(
            @PathVariable String taskId,
            @RequestBody Map<String, String> request) {
        
        try {
            LocalDateTime lastRun = LocalDateTime.parse(request.get("lastRun"));
            log.info("Updating last run time for task {} to {}", taskId, lastRun);
            
            return agentSchedulingService.updateTaskLastRunTime(taskId, lastRun)
                    .map(ResponseEntity::ok)
                    .onErrorReturn(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error parsing lastRun date: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .body(null)); // Could return error details here
        }
    }
    
} 