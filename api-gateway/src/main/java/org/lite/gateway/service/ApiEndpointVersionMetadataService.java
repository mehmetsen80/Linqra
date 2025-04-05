package org.lite.gateway.service;

import org.lite.gateway.entity.ApiEndpointVersionMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ApiEndpointVersionMetadataService {
    Mono<ApiEndpointVersionMetadata> saveVersionMetadata(
        String endpointId,
        String routeIdentifier,
        Integer version,
        String changeReason,
        String changeDescription,
        Map<String, Object> changedFields,
        String changedBy,
        ApiEndpointVersionMetadata.ChangeType changeType
    );

    Flux<ApiEndpointVersionMetadata> getVersionMetadataByEndpointId(String endpointId);
    Flux<ApiEndpointVersionMetadata> getVersionMetadataByRouteIdentifier(String routeIdentifier);
    Mono<ApiEndpointVersionMetadata> getVersionMetadata(String endpointId, Integer version);
} 