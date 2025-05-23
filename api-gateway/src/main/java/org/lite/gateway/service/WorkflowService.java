package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import reactor.core.publisher.Mono;

public interface WorkflowService {
    Mono<LinqResponse> executeWorkflow(LinqRequest request);
}