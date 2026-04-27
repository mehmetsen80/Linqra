package org.lite.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.CacheRequestDTO;
import org.lite.gateway.service.CacheService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/linq/cache")
@Slf4j
@AllArgsConstructor
public class CacheController {

        private final CacheService cacheService;
        private final TeamContextService teamContextService;

        private static final String KEY_PREFIX = "app_cache:";

        @GetMapping("/{key}")
        public Mono<Map<String, String>> get(
                        @PathVariable String key,
                        ServerWebExchange exchange) {
                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                        String fullKey = KEY_PREFIX + teamId + ":" + key;
                                        return cacheService.get(fullKey)
                                                        .map(value -> Map.of("key", key, "value", value))
                                                        .switchIfEmpty(Mono.error(
                                                                        new org.springframework.web.server.ResponseStatusException(
                                                                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                                                                        "Key not found in cache")));
                                });
        }

        @PostMapping
        public Mono<Map<String, Object>> set(
                        @RequestBody CacheRequestDTO request,
                        ServerWebExchange exchange) {
                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                        String fullKey = KEY_PREFIX + teamId + ":" + request.getKey();
                                        Duration ttl = request.getTtlSeconds() != null
                                                        ? Duration.ofSeconds(request.getTtlSeconds())
                                                        : Duration.ofHours(24);

                                        return cacheService.set(fullKey, request.getValue(), ttl)
                                                        .then(Mono.just(Map.of(
                                                                        "status", "success",
                                                                        "key", request.getKey(),
                                                                        "ttlSeconds", ttl.getSeconds())));
                                });
        }

        @DeleteMapping("/{key}")
        public Mono<Map<String, String>> delete(
                        @PathVariable String key,
                        ServerWebExchange exchange) {
                return teamContextService.getTeamFromContext(exchange)
                                .flatMap(teamId -> {
                                        String fullKey = KEY_PREFIX + teamId + ":" + key;
                                        return cacheService.delete(fullKey)
                                                        .map(deleted -> Map.of("key", key, "deleted",
                                                                        String.valueOf(deleted)));
                                });
        }
}
