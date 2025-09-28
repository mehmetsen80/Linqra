package org.lite.gateway.dto;

import lombok.Data;

@Data
public class AgentSchedulingRequest {
    private String cronExpression;
} 