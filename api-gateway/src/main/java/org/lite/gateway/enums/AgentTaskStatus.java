package org.lite.gateway.enums;

public enum AgentTaskStatus {
    PENDING,        // Task is waiting to be executed
    READY,          // Task is ready to execute
    RUNNING,        // Task is currently executing
    COMPLETED,      // Task completed successfully
    FAILED,         // Task failed during execution
    SKIPPED,        // Task was skipped (prerequisites not met)
    CANCELLED,      // Task was cancelled before execution
    TIMEOUT         // Task timed out during execution
} 