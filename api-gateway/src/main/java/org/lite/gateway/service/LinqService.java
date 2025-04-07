package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import reactor.core.publisher.Mono;

public interface LinqService {

    Mono<LinqResponse> processLinqRequest(LinqRequest request);
    Mono<LinqProtocolExample> convertToLinqProtocol(SwaggerEndpointInfo endpointInfo, String routeIdentifier);
}
