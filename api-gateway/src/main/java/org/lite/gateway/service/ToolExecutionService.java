package org.lite.gateway.service;

import org.lite.gateway.dto.CallerParams;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.ToolDefinition;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ToolExecutionService {
    Mono<LinqResponse> executeTool(LinqRequest request, CallerParams callerParams);

    Mono<LinqResponse> executeTool(LinqRequest request, CallerParams callerParams, boolean forceRefresh);

    Mono<LinqResponse> executeToolDryRun(ToolDefinition tool, Map<String, Object> params, String executedBy);
}
