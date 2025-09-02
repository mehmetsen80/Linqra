package org.lite.gateway.enums;

public enum AgentStatus {
    IDLE,           // Agent is ready to execute
    RUNNING,        // Agent is currently executing a task
    WAITING,        // Agent is waiting for a dependency or resource
    COMPLETED,      // Agent has completed its task successfully
    ERROR,          // Agent encountered an error during execution
    SCHEDULED,      // Agent is scheduled for future execution
    DISABLED        // Agent is disabled and cannot execute
} 