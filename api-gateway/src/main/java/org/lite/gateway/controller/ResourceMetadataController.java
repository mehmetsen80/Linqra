package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ResourceMetadata;
import org.lite.gateway.repository.ResourceMetadataRepository;
import org.lite.gateway.repository.ResourceSubscriptionRepository;
import org.lite.gateway.repository.ResourceUpdateNotificationRepository;
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
    private final ResourceSubscriptionRepository resourceSubscriptionRepository;
    private final ResourceUpdateNotificationRepository resourceUpdateNotificationRepository;

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
    public Mono<ResponseEntity<Object>> deleteResource(@PathVariable String domain, @PathVariable String category, @PathVariable String resourceId) {
        log.info("Request to delete resource metadata: {}/{}/{}", domain, category, resourceId);
        return resourceSubscriptionRepository.existsByDomainAndCategoryAndResourceId(domain, category, resourceId)
                .flatMap(existsInSubscription -> {
                    if (existsInSubscription) {
                        log.warn("Refusing to delete resource metadata: currently used in active subscriptions");
                        return Mono.just(ResponseEntity.badRequest().body("Cannot delete metadata: Resource is currently used in active subscriptions."));
                    }
                    return resourceUpdateNotificationRepository.existsByDomainAndCategoryAndResourceId(domain, category, resourceId)
                            .flatMap(existsInNotification -> {
                                if (existsInNotification) {
                                    log.warn("Refusing to delete resource metadata: currently referenced in notification logs");
                                    return Mono.just(ResponseEntity.badRequest().body("Cannot delete metadata: Resource is currently referenced in notification logs."));
                                }
                                return resourceMetadataRepository.findByDomainAndCategoryAndResourceId(domain, category, resourceId)
                                        .flatMap(existing -> {
                                            log.info("Deleting resource metadata {}/{}/{}", domain, category, resourceId);
                                            return resourceMetadataRepository.delete(existing)
                                                    .then(Mono.just(ResponseEntity.noContent().build()));
                                        })
                                        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
                            });
                });
    }
}
