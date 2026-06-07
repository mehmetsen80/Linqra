package org.lite.gateway.repository;

import java.util.Collection;

import org.springframework.data.mongodb.repository.Query;
import org.lite.gateway.entity.ResourceSubscription;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceSubscriptionRepository extends ReactiveMongoRepository<ResourceSubscription, String> {
        @Query("{ '$or': [ { 'userId': ?0 }, { 'delivery.email': ?0 }, { 'teamId': { '$in': ?1 } } ] }")
        Flux<ResourceSubscription> findByUserIdOrEmailOrTeamIdIn(String userId, Collection<String> teamIds);

        @Query("{ '$or': [ { 'userId': { '$in': ?0 } }, { 'delivery.email': { '$in': ?0 } }, { 'teamId': { '$in': ?1 } } ] }")
        Flux<ResourceSubscription> findByUserIdInOrEmailInOrTeamIdIn(Collection<String> userIds,
                        Collection<String> teamIds);

        Flux<ResourceSubscription> findByUserId(String userId);

        Flux<ResourceSubscription> findByUserIdIn(Collection<String> userIds);

        Flux<ResourceSubscription> findByAppNameIn(Collection<String> appNames);

        Flux<ResourceSubscription> findByTeamId(String teamId);

        Flux<ResourceSubscription> findByDomainAndCategoryAndResourceIdAndEnabledTrue(String domain, String category,
                        String resourceId);

        Mono<Boolean> existsByDomainAndCategoryAndResourceId(String domain, String category, String resourceId);

        Mono<ResourceSubscription> findByUserIdAndDomainAndCategoryAndResourceIdAndAppName(String userId,
                        String domain, String category,
                        String resourceId, String appName);

        Mono<ResourceSubscription> findByTeamIdAndDomainAndCategoryAndResourceIdAndAppName(String teamId,
                        String domain, String category,
                        String resourceId, String appName);
}
