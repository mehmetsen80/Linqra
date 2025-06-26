package org.lite.gateway.service;

import java.util.Map;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
public class WorkflowExecutionContext {
    
    private final Map<Integer, Object> stepResults;
    private final Map<String, Object> globalParams;
}
