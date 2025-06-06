package org.lite.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiRoute;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final Set<String> whitelistedPaths = new CopyOnWriteArraySet<>() {{
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
    }};

    private final Map<String, String> clientScopes = new ConcurrentHashMap<>();

    private final RedisTemplate<String, String> redisTemplate;
    private final ChannelTopic routesTopic;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        // Load whitelisted paths from Redis
        Set<String> initialRoutes = redisTemplate.opsForSet().members("whitelistedPaths");

        if (initialRoutes == null || initialRoutes.isEmpty()) {
            // If Redis is empty, add all default paths to Redis
            whitelistedPaths.forEach(path -> redisTemplate.opsForSet().add("whitelistedPaths", path));
        } else {
            // Otherwise, add any missing default paths to both Redis and in-memory set
            whitelistedPaths.forEach(path -> {
                if (!initialRoutes.contains(path)) {
                    redisTemplate.opsForSet().add("whitelistedPaths", path);
                }
            });

            // Add Redis-loaded paths to in-memory whitelist
            whitelistedPaths.addAll(initialRoutes);
        }

        //initialize the existing client scopes
        for (Map.Entry<String, String> entry : clientScopes.entrySet()) {
            redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
        }
    }

    public String getClientScope(String path){
        return redisTemplate.opsForValue().get(path);
    }

    // Add a path to the whitelist
    public void addPath(ApiRoute apiRoute) {
        // Add path to Redis and publish to notify other instances
        redisTemplate.opsForSet().add("whitelistedPaths", apiRoute.getPath());
        redisTemplate.convertAndSend(routesTopic.getTopic(), "ADD PATH:" + apiRoute.getPath());
        whitelistedPaths.add(apiRoute.getPath());
    }

    public void addScope(ApiRoute apiRoute){
        redisTemplate.opsForValue().set(apiRoute.getPath(), apiRoute.getScope());
        redisTemplate.convertAndSend(routesTopic.getTopic(), "ADD SCOPE:" + apiRoute.getScope());
        clientScopes.putIfAbsent(apiRoute.getPath(), apiRoute.getScope());
    }

    // Remove a path from the whitelist, TODO: Not used right now but will add the logic to the UI
    public void removePath(ApiRoute apiRoute) {
        // Remove path from Redis and publish to notify other instances
        redisTemplate.opsForSet().remove("whitelistedPaths", apiRoute.getPath());
        redisTemplate.convertAndSend(routesTopic.getTopic(), "REMOVE PATH:" + apiRoute.getPath());
        whitelistedPaths.remove(apiRoute.getPath());
    }

    // Remove a scope from the whitelist, TODO: Not used right now but will add the logic to the UI
    public void removeScope(ApiRoute apiRoute) {
        // Remove scope from Redis and publish to notify other instances
        redisTemplate.opsForValue().set(apiRoute.getPath(), apiRoute.getScope());
        redisTemplate.convertAndSend(routesTopic.getTopic(), "REMOVE SCOPE:" + apiRoute.getScope());
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