package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.scheduler.AgentTaskQuartzJob;
import org.lite.gateway.service.AgentQuartzService;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentQuartzServiceImpl implements AgentQuartzService {

    private final Scheduler scheduler;

    private static final String JOB_GROUP = "agentTasks";
    private static final String TRIGGER_GROUP = "agentTasksTriggers";

    @Override
    public void scheduleTask(String taskId, String cronExpression, String teamId, String executedBy) throws Exception {
        JobKey jobKey = new JobKey(taskId, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(taskId, TRIGGER_GROUP);

        JobDataMap dataMap = new JobDataMap();
        dataMap.put("taskId", taskId);
        dataMap.put("teamId", teamId);
        dataMap.put("executedBy", executedBy);

        JobDetail jobDetail = JobBuilder.newJob(AgentTaskQuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData(dataMap)
                .storeDurably(false)
                .build();

        CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(cronExpression)
                .inTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                .withMisfireHandlingInstructionDoNothing();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobDetail)
                .withSchedule(cronSchedule)
                .build();

        if (scheduler.checkExists(jobKey)) {
            log.info("[Quartz] Rescheduling existing job for task {}", taskId);
            scheduler.deleteJob(jobKey);
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("[Quartz] Scheduled task {} with cron {}", taskId, cronExpression);
    }

    @Override
    public void unscheduleTask(String taskId) throws Exception {
        JobKey jobKey = new JobKey(taskId, JOB_GROUP);
        TriggerKey triggerKey = new TriggerKey(taskId, TRIGGER_GROUP);
        if (scheduler.checkExists(triggerKey)) {
            scheduler.unscheduleJob(triggerKey);
        }
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        log.info("[Quartz] Unscheduled task {}", taskId);
    }
} 