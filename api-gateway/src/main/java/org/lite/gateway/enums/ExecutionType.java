package org.lite.gateway.enums;

public enum ExecutionType {
    SCHEDULED,      // Execution triggered by cron schedule
    MANUAL,         // Execution triggered manually by user
    EVENT_DRIVEN,   // Execution triggered by an event
    WORKFLOW,       // Execution triggered by workflow
    AGENT_SCHEDULED // Execution triggered by agent scheduling
} 