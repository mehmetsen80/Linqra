package org.lite.gateway.model;

import lombok.Data;

@Data
public class LinqWorkflowStats {
    private int totalExecutions;
    private int successfulExecutions;
    private int failedExecutions;
    private double averageExecutionTime;
}
