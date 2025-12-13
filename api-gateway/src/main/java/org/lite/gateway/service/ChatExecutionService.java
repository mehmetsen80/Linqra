package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import reactor.core.publisher.Mono;

public interface ChatExecutionService {
    
    // Execute chat request
    Mono<LinqResponse> executeChat(LinqRequest request);
}

