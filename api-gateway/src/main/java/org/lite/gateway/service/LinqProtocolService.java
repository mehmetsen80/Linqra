package org.lite.gateway.service;

import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import reactor.core.publisher.Mono;

public interface LinqProtocolService {
    Mono<LinqProtocolExample> convertToLinqProtocol(SwaggerEndpointInfo endpointInfo, String routeIdentifier);
}
