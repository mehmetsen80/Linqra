package org.lite.gateway.enums;

/**
 * Enum representing different types of execution triggers for agent tasks
 */
public enum ExecutionTrigger {
    /**
     * Task is triggered manually by a user or API call
     */
    MANUAL,
    
    /**
     * Task is triggered by a cron schedule
     */
    CRON,
    
    /**
     * Task is triggered by external events or webhooks
     */
    EVENT_DRIVEN,
    
    /**
     * Task is triggered as part of another workflow
     */
    WORKFLOW
} 