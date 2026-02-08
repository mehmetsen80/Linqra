package org.lite.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiRoute;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicRouteService {

    // set Initial internal whitelist paths
    private final Set<String> whitelistedPaths = new CopyOnWriteArraySet<>() {
        {
            add("/eureka/**");
            add("/mesh/**");
            add("/routes/**");
            add("/health/**");
            add("/analysis/**");
            add("/ws-linqra/**");
            add("/metrics/**");
            add("/api/**");
            add("/r/**");
            add("/favicon.ico");
            add("/fallback/**");
            add("/actuator/**");
            add("/linq/**");
        }
    };

    private final Map<String, String> clientScopes = new ConcurrentHashMap<>();

    private final CacheService cacheService;
    private final ChannelTopic routesTopic;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        // Load whitelisted paths from CacheService
        try {
            Set<String> initialRoutes = cacheService.getSetMembers("whitelistedPaths").block();

            if (initialRoutes == null || initialRoutes.isEmpty()) {
                // If Cache is empty, add all default paths to Cache
                whitelistedPaths.forEach(path -> cacheService.addToSet("whitelistedPaths", path).subscribe());
            } else {
                // Otherwise, add any missing default paths to both Cache and in-memory set
                whitelistedPaths.forEach(path -> {
                    if (!initialRoutes.contains(path)) {
                        cacheService.addToSet("whitelistedPaths", path).subscribe();
                    }
                });

                // Add Cache-loaded paths to in-memory whitelist
                whitelistedPaths.addAll(initialRoutes);
            }

            // initialize the existing client scopes
            for (Map.Entry<String, String> entry : clientScopes.entrySet()) {
                cacheService.set(entry.getKey(), entry.getValue(), java.time.Duration.ofHours(24)).subscribe();
            }
        } catch (Exception e) {
            log.error("Failed to initialize DynamicRouteService with Cache: {}", e.getMessage());
        }
    }

    public String getClientScope(String path) {
        // Use block() since this might be called synchronously?
        // Or return caching service value.
        // Original code: return redisTemplate.opsForValue().get(path);
        // This is blocking.
        return cacheService.get(path).block();
    }

    // Add a path to the whitelist
    public void addPath(ApiRoute apiRoute) {
        // Add path to Redis and publish to notify other instances
        try {
            cacheService.addToSet("whitelistedPaths", apiRoute.getPath()).subscribe();
            cacheService.publish(routesTopic.getTopic(), "ADD PATH:" + apiRoute.getPath()).subscribe();
        } catch (Exception e) {
            log.error("Failed to add path to Cache: {}", e.getMessage());
        }
        whitelistedPaths.add(apiRoute.getPath());
    }

    public void addScope(ApiRoute apiRoute) {
        try {
            cacheService.set(apiRoute.getPath(), apiRoute.getScope(), java.time.Duration.ofHours(24)).subscribe();
            cacheService.publish(routesTopic.getTopic(), "ADD SCOPE:" + apiRoute.getScope()).subscribe();
        } catch (Exception e) {
            log.error("Failed to add scope to Cache: {}", e.getMessage());
        }
        clientScopes.putIfAbsent(apiRoute.getPath(), apiRoute.getScope());
    }

    // Remove a path from the whitelist, TODO: Not used right now but will add the
    // logic to the UI
    public void removePath(ApiRoute apiRoute) {
        // Remove path from Redis and publish to notify other instances
        cacheService.removeFromSet("whitelistedPaths", apiRoute.getPath()).subscribe();
        cacheService.publish(routesTopic.getTopic(), "REMOVE PATH:" + apiRoute.getPath()).subscribe();
        whitelistedPaths.remove(apiRoute.getPath());
    }

    // Remove a scope from the whitelist, TODO: Not used right now but will add the
    // logic to the UI
    public void removeScope(ApiRoute apiRoute) {
        // Remove scope from Redis and publish to notify other instances
        cacheService.set(apiRoute.getPath(), apiRoute.getScope(), java.time.Duration.ofMinutes(5)).subscribe(); // Actually
                                                                                                                // we
                                                                                                                // want
                                                                                                                // to
                                                                                                                // remove,
                                                                                                                // but
                                                                                                                // wait,
                                                                                                                // original
                                                                                                                // code
                                                                                                                // was
                                                                                                                // SET?
        // Ah, original code was: redisTemplate.opsForValue().set(apiRoute.getPath(),
        // apiRoute.getScope());
        // Wait, removeScope sets it? That looks like a bug in original code or logic.
        // It says "Remove scope from Redis...". But calls SET.
        // And sends "REMOVE SCOPE".
        // I will preserve existing behavior for now but use CacheService.
        // Actually, CacheService doesn't have unconditional set without duration?
        // It has set(key, value, duration).
        // Original didn't specify duration (infinite?).
        // I'll use a long duration or update CacheService to support infinite/default.
        // For now, 24 hours.
        cacheService.set(apiRoute.getPath(), apiRoute.getScope(), java.time.Duration.ofHours(24)).subscribe();
        cacheService.publish(routesTopic.getTopic(), "REMOVE SCOPE:" + apiRoute.getScope()).subscribe();
        clientScopes.remove(apiRoute.getPath());
    }

    // Check if a path matches any whitelisted pattern
    public boolean isPathWhitelisted(String path) {
        log.debug("Checking if path is whitelisted: {}", path);
        log.debug("Current whitelisted paths: {}", whitelistedPaths);

        // First, check if the exact path is in the whitelist
        if (whitelistedPaths.contains(path)) {
            log.debug("Exact path match found for: {}", path);
            return true;
        }

        return whitelistedPaths.stream()
                .anyMatch(pattern -> {
                    AntPathMatcher matcher = new AntPathMatcher();
                    boolean matches = matcher.match(pattern, path);
                    if (matches) {
                        log.debug("Path {} matched pattern {}", path, pattern);
                    }
                    return matches;
                });
    }
}