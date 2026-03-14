package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ResourceSubscriptionRequest;
import org.lite.gateway.entity.ResourceSubscription;
import org.lite.gateway.service.ResourceSubscriptionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class ResourceSubscriptionController {

        private final ResourceSubscriptionService subscriptionService;
        private final TeamContextService teamContextService;
        private final UserContextService userContextService;

        @PostMapping("/subscribe/user")
        public Mono<ResponseEntity<ResourceSubscription>> subscribeUser(
                        @RequestBody ResourceSubscriptionRequest request,
                        ServerWebExchange exchange) {

                return userContextService.getCurrentUsername(exchange)
                                .flatMap(
                                                username -> subscriptionService.subscribeUser(username,
                                                                request.getResourceCategory(),
                                                                request.getResourceId(), request.getDelivery()))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/subscribe/team")
        public Mono<ResponseEntity<ResourceSubscription>> subscribeTeam(
                        @RequestBody ResourceSubscriptionRequest request,
                        ServerWebExchange exchange) {

                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> subscriptionService.subscribeTeam(teamId,
                                                request.getResourceCategory(),
                                                request.getResourceId(), request.getDelivery()))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/user")
        public Flux<ResourceSubscription> getMySubscriptions(ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMapMany(subscriptionService::getSubscriptionsForUser);
        }

        @GetMapping("/team")
        public Flux<ResourceSubscription> getTeamSubscriptions(ServerWebExchange exchange) {
                return teamContextService.getTeamFromContext(exchange)
                                .flatMapMany(subscriptionService::getSubscriptionsForTeam);
        }

        @DeleteMapping("/{subscriptionId}")
        public Mono<ResponseEntity<Void>> unsubscribe(@PathVariable String subscriptionId) {
                return subscriptionService.unsubscribe(subscriptionId)
                                .then(Mono.just(ResponseEntity.noContent().build()));
        }
}
