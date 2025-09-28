package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.AgentQuartzService;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuartzStartupScheduler implements ApplicationListener<ContextRefreshedEvent> {

    private final AgentTaskRepository agentTaskRepository;
    private final AgentRepository agentRepository;
    private final AgentQuartzService agentQuartzService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("QuartzStartupScheduler: scanning for CRON tasks to schedule on startup...");
        agentTaskRepository.findCronTasksToScheduleOnStartup()
                .flatMap(task -> agentRepository.findById(task.getAgentId())
                        .flatMap(agent -> {
                            String taskId = task.getId();
                            String cron = task.getCronExpression();
                            String teamId = agent.getTeamId();
                            log.info("Registering Quartz job for task {} with cron {} (team {})", taskId, cron, teamId);
                            return Mono.fromCallable(() -> {
                                        agentQuartzService.scheduleTask(taskId, cron, teamId, "scheduler");
                                        return true;
                                    })
                                    .doOnError(e -> log.error("Failed to schedule task {} on startup: {}", taskId, e.getMessage()))
                                    .onErrorResume(e -> Mono.empty());
                        }))
                .doOnError(err -> log.error("Error scanning tasks for startup scheduling: {}", err.getMessage()))
                .subscribe();
    }
} 