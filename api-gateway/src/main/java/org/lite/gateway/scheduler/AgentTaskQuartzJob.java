package org.lite.gateway.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.CronCalculationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Quartz job that runs a scheduled AgentTask by taskId.
 */
@Slf4j
@Component
@DisallowConcurrentExecution
public class AgentTaskQuartzJob extends QuartzJobBean {

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentExecutionService agentExecutionService;
    
    @Autowired
    private CronCalculationService cronCalculationService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String taskId = context.getMergedJobDataMap().getString("taskId");
        String teamId = context.getMergedJobDataMap().getString("teamId");
        String executedBy = context.getMergedJobDataMap().getString("executedBy");

        log.info("[Quartz] Executing scheduled AgentTask: {} (teamId={}, executedBy={})", taskId, teamId, executedBy);

        try {
            // Load task to get agentId
            AgentTask task = agentTaskRepository.findById(taskId).block();
            if (task == null) {
                log.error("[Quartz] AgentTask not found: {}", taskId);
                return;
            }
            String agentId = task.getAgentId();
            if (agentId == null) {
                log.error("[Quartz] AgentTask {} has no agentId", taskId);
                return;
            }

            // Update lastRun to current time (in UTC to match cron scheduling)
            LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("UTC"));
            task.setLastRun(now);
            
            // Calculate and update nextRun if cron is set
            if (task.getCronExpression() != null && !task.getCronExpression().trim().isEmpty()) {
                LocalDateTime nextRun = cronCalculationService.calculateNextRunAfter(task.getCronExpression(), now);
                task.setNextRun(nextRun);
                log.info("[Quartz] Updated task {} - lastRun: {}, nextRun: {}", taskId, now, nextRun);
            }
            
            // Save updated times
            agentTaskRepository.save(task).block();

            // Start execution (blocking within Quartz job thread)
            agentExecutionService.startTaskExecution(agentId, taskId, teamId, executedBy != null ? executedBy : "scheduler")
                    .onErrorResume(err -> {
                        log.error("[Quartz] Failed to start execution for task {}: {}", taskId, err.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("[Quartz] Unexpected error executing task {}: {}", taskId, e.getMessage(), e);
        }
    }
} 