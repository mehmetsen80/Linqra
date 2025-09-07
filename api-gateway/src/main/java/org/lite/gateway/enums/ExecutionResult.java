package org.lite.gateway.enums;

public enum ExecutionResult {
    SUCCESS,            // Execution completed successfully
    PARTIAL_SUCCESS,    // Execution completed with partial success
    FAILURE,            // Execution failed
    SKIPPED,            // Execution was skipped
    UNKNOWN             // Execution result is unknown (initial state)
} 