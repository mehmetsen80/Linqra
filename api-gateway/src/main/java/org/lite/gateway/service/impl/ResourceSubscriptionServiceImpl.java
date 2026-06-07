package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ResourceSubscription;
import org.lite.gateway.entity.ApiRoute;
import org.lite.gateway.repository.ResourceMetadataRepository;
import org.lite.gateway.repository.ResourceSubscriptionRepository;
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.repository.ApiRouteRepository;
import org.lite.gateway.repository.TeamMemberRepository;
import org.lite.gateway.entity.TeamMember;
import org.lite.gateway.service.ResourceSubscriptionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceSubscriptionServiceImpl implements ResourceSubscriptionService {

    private final ResourceSubscriptionRepository subscriptionRepository;
    private final ResourceMetadataRepository resourceMetadataRepository;
    private final TeamRouteRepository teamRouteRepository;
    private final ApiRouteRepository apiRouteRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public Mono<ResourceSubscription> subscribeUser(String userId, String domain, String category, String resourceId,
            String appName, ResourceSubscription.DeliveryConfig delivery) {
        return validateResource(domain, category, resourceId)
                .then(subscriptionRepository
                        .findByUserIdAndDomainAndCategoryAndResourceIdAndAppName(userId, domain, category, resourceId,
                                appName)
                        .flatMap(existing -> {
                            existing.setDelivery(delivery);
                            existing.setEnabled(true);
                            existing.setUpdatedAt(LocalDateTime.now());
                            return subscriptionRepository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            ResourceSubscription sub = ResourceSubscription.builder()
                                    .userId(userId)
                                    .domain(domain)
                                    .category(category)
                                    .resourceId(resourceId)
                                    .appName(appName)
                                    .delivery(delivery)
                                    .enabled(true)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            return subscriptionRepository.save(sub);
                        })));
    }

    @Override
    public Mono<ResourceSubscription> subscribeTeam(String teamId, String domain, String category, String resourceId,
            String appName, ResourceSubscription.DeliveryConfig delivery) {
        return validateResource(domain, category, resourceId)
                .then(subscriptionRepository
                        .findByTeamIdAndDomainAndCategoryAndResourceIdAndAppName(teamId, domain, category, resourceId,
                                appName)
                        .flatMap(existing -> {
                            existing.setDelivery(delivery);
                            existing.setEnabled(true);
                            existing.setUpdatedAt(LocalDateTime.now());
                            return subscriptionRepository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            ResourceSubscription sub = ResourceSubscription.builder()
                                    .teamId(teamId)
                                    .domain(domain)
                                    .category(category)
                                    .resourceId(resourceId)
                                    .appName(appName)
                                    .delivery(delivery)
                                    .enabled(true)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            return subscriptionRepository.save(sub);
                        })));
    }

    private Mono<Void> validateResource(String domain, String category, String resourceId) {
        return resourceMetadataRepository.existsByDomainAndCategoryAndResourceId(domain, category, resourceId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Invalid resource domain/category or ID: " + domain + "/" + category + "/"
                                        + resourceId));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> unsubscribe(String subscriptionId) {
        return subscriptionRepository.deleteById(subscriptionId);
    }

    @Override
    public Flux<ResourceSubscription> getSubscriptionsForUser(String userId) {
        return teamMemberRepository.findByUserId(userId)
                .map(TeamMember::getTeamId)
                .collectList()
                .flatMapMany(teamIds -> subscriptionRepository.findByUserIdOrEmailOrTeamIdIn(userId, teamIds));
    }

    @Override
    public Flux<ResourceSubscription> getSubscriptionsForUser(java.util.List<String> userIds) {
        return Flux.fromIterable(userIds)
                .flatMap(teamMemberRepository::findByUserId)
                .map(TeamMember::getTeamId)
                .collectList()
                .flatMapMany(teamIds -> subscriptionRepository.findByUserIdInOrEmailInOrTeamIdIn(userIds, teamIds));
    }

    @Override
    public Flux<ResourceSubscription> getSubscriptionsForTeam(String teamId) {
        Flux<ResourceSubscription> byTeamId = subscriptionRepository.findByTeamId(teamId);

        Flux<ResourceSubscription> byTeamRoutes = teamRouteRepository.findByTeamId(teamId)
                .flatMap(teamRoute -> apiRouteRepository.findById(teamRoute.getRouteId()))
                .map(ApiRoute::getRouteIdentifier)
                .filter(java.util.Objects::nonNull)
                .collectList()
                .flatMapMany(routeIdentifiers -> {
                    log.info("Fetching subscriptions for team {} associated with route identifiers: {}", teamId,
                            routeIdentifiers);
                    if (routeIdentifiers.isEmpty()) {
                        return Flux.empty();
                    }
                    return subscriptionRepository.findByAppNameIn(routeIdentifiers);
                });

        return Flux.merge(byTeamId, byTeamRoutes)
                .distinct(ResourceSubscription::getId);
    }

    @Override
    public Flux<ResourceSubscription> getActiveSubscriptionsForResource(String domain, String category,
            String resourceId) {
        return subscriptionRepository.findByDomainAndCategoryAndResourceIdAndEnabledTrue(domain, category, resourceId);
    }

    @Override
    public Mono<ResourceSubscription> updateSubscription(String subscriptionId,
            ResourceSubscription.DeliveryConfig delivery,
            boolean enabled) {
        return subscriptionRepository.findById(subscriptionId)
                .flatMap(sub -> {
                    sub.setDelivery(delivery);
                    sub.setEnabled(enabled);
                    sub.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepository.save(sub);
                });
    }
}
