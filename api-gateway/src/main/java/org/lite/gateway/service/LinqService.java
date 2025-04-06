package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.LinqProtocolExample;
import reactor.core.publisher.Mono;

public interface LinqService {

    Mono<LinqResponse> processLinqRequest(LinqRequest request);
    Mono<LinqProtocolExample> convertToLinqProtocol(String method, String path, Object schema, String routeIdentifier);
}
