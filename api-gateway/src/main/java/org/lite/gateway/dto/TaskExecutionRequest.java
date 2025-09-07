package org.lite.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Task execution request")
public class TaskExecutionRequest {
    
    @Schema(description = "Additional execution parameters (optional)", example = "{}")
    private Object executionParams;
    
    public TaskExecutionRequest() {}
    
    public TaskExecutionRequest(Object executionParams) {
        this.executionParams = executionParams;
    }
    
    // Getters and Setters
    public Object getExecutionParams() { return executionParams; }
    public void setExecutionParams(Object executionParams) { this.executionParams = executionParams; }
} 