package org.lite.gateway.controller;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.service.AgentSchedulingService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks/scheduling")
@RequiredArgsConstructor
@Slf4j
public class AgentSchedulingController {
    
    private final AgentSchedulingService agentSchedulingService;
    
    // ==================== TASK SCHEDULING OPERATIONS ====================
    
    @PostMapping("/{taskId}/schedule")
    public Mono<ResponseEntity<AgentTask>> scheduleTask(
            @PathVariable String taskId,
            @RequestParam String cronExpression,
            @RequestParam String teamId) {
        
        log.info("Scheduling task {} with cron: {} for team {}", taskId, cronExpression, teamId);
        
        return agentSchedulingService.scheduleTask(taskId, cronExpression, teamId)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @PostMapping("/{taskId}/unschedule")
    public Mono<ResponseEntity<AgentTask>> unscheduleTask(
            @PathVariable String taskId,
            @RequestParam String teamId) {
        
        log.info("Unscheduling task {} for team {}", taskId, teamId);
        
        return agentSchedulingService.unscheduleTask(taskId, teamId)
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