package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ResourceMetadata;
import org.lite.gateway.repository.ResourceMetadataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
@Slf4j
public class ResourceMetadataController {

    private final ResourceMetadataRepository resourceMetadataRepository;

    @PostMapping
    public Mono<ResponseEntity<ResourceMetadata>> upsertResource(@RequestBody ResourceMetadata metadata) {
        return resourceMetadataRepository.findByDomainAndCategoryAndResourceId(metadata.getDomain(), metadata.getCategory(), metadata.getResourceId())
                .flatMap(existing -> {
                    log.info("Updating existing resource metadata: {}/{}/{}", metadata.getDomain(), metadata.getCategory(),
                            metadata.getResourceId());
                    existing.setDisplayName(metadata.getDisplayName());
                    existing.setDescription(metadata.getDescription());
                    return resourceMetadataRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Creating new resource metadata: {}/{}/{}", metadata.getDomain(), metadata.getCategory(), metadata.getResourceId());
                    return resourceMetadataRepository.save(metadata);
                }))
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Flux<ResourceMetadata> getAllResources() {
        return resourceMetadataRepository.findAll();
    }

    @DeleteMapping("/{domain}/{category}/{resourceId}")
    public Mono<ResponseEntity<Void>> deleteResource(@PathVariable String domain, @PathVariable String category, @PathVariable String resourceId) {
        return resourceMetadataRepository.findByDomainAndCategoryAndResourceId(domain, category, resourceId)
                .flatMap(resourceMetadataRepository::delete)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
