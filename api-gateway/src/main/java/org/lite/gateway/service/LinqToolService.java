package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqTool;
import reactor.core.publisher.Mono;

public interface LinqToolService {
    Mono<LinqTool> saveLinqTool(LinqTool linqTool);
    Mono<LinqResponse> executeToolRequest(LinqRequest request, LinqTool tool);
}
