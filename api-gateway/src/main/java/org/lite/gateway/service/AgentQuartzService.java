package org.lite.gateway.service;

public interface AgentQuartzService {
    void scheduleTask(String taskId, String cronExpression, String teamId, String executedBy) throws Exception;
    void unscheduleTask(String taskId) throws Exception;
} 