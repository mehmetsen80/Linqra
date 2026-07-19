package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public interface ChatExecutionService {
    
    // Execute chat request
    Mono<LinqResponse> executeChat(LinqRequest request);

    // Execute chat request and stream events to a sink
    default Mono<LinqResponse> executeChat(LinqRequest request, Sinks.Many<org.springframework.http.codec.ServerSentEvent<String>> sseSink) {
        return executeChat(request); // Default implementation for backwards compatibility
    }
}

