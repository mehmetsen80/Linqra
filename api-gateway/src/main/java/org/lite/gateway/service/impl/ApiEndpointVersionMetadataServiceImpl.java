package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiEndpointVersionMetadata;
import org.lite.gateway.repository.ApiEndpointVersionMetadataRepository;
import org.lite.gateway.service.ApiEndpointVersionMetadataService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiEndpointVersionMetadataServiceImpl implements ApiEndpointVersionMetadataService {
    
    private final ApiEndpointVersionMetadataRepository metadataRepository;

    @Override
    public Mono<ApiEndpointVersionMetadata> saveVersionMetadata(
            String endpointId,
            String routeIdentifier,
            Integer version,
            String changeReason,
            String changeDescription,
            Map<String, Object> changedFields,
            String changedBy,
            ApiEndpointVersionMetadata.ChangeType changeType) {
        
        ApiEndpointVersionMetadata metadata = ApiEndpointVersionMetadata.builder()
                .endpointId(endpointId)
                .routeIdentifier(routeIdentifier)
                .version(version)
                .changeReason(changeReason)
                .changeDescription(changeDescription)
                .changedFields(changedFields)
                .changedBy(changedBy)
                .createdAt(System.currentTimeMillis())
                .changeType(changeType)
                .build();

        return metadataRepository.save(metadata)
                .doOnSuccess(saved -> log.info("Saved version metadata for endpoint: {} version: {}", 
                    endpointId, version))
                .doOnError(error -> log.error("Error saving version metadata for endpoint: {} version: {}", 
                    endpointId, version, error));
    }

    @Override
    public Flux<ApiEndpointVersionMetadata> getVersionMetadataByEndpointId(String endpointId) {
        return metadataRepository.findByEndpointIdOrderByVersionDesc(endpointId)
                .doOnComplete(() -> log.info("Retrieved version metadata for endpoint: {}", endpointId))
                .doOnError(error -> log.error("Error retrieving version metadata for endpoint: {}", 
                    endpointId, error));
    }

    @Override
    public Flux<ApiEndpointVersionMetadata> getVersionMetadataByRouteIdentifier(String routeIdentifier) {
        return metadataRepository.findByRouteIdentifierOrderByVersionDesc(routeIdentifier)
                .doOnComplete(() -> log.info("Retrieved version metadata for route: {}", routeIdentifier))
                .doOnError(error -> log.error("Error retrieving version metadata for route: {}", 
                    routeIdentifier, error));
    }

    @Override
    public Mono<ApiEndpointVersionMetadata> getVersionMetadata(String endpointId, Integer version) {
        return metadataRepository.findByEndpointIdAndVersion(endpointId, version)
                .doOnSuccess(metadata -> log.info("Retrieved version metadata for endpoint: {} version: {}", 
                    endpointId, version))
                .doOnError(error -> log.error("Error retrieving version metadata for endpoint: {} version: {}", 
                    endpointId, version, error));
    }
} 