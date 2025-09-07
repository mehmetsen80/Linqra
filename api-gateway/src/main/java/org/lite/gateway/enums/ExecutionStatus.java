package org.lite.gateway.enums;

public enum ExecutionStatus {
    RUNNING,        // Execution is currently running
    COMPLETED,      // Execution completed successfully
    FAILED,         // Execution failed
    CANCELLED,      // Execution was cancelled
    TIMEOUT         // Execution timed out
} 