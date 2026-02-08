package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiMetric;
import org.lite.gateway.entity.ApiRoute;
import org.lite.gateway.entity.FilterConfig;
import org.lite.gateway.service.*;
import org.springframework.beans.BeansException;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@Slf4j
public class ApiRouteLocatorImpl implements RouteLocator, ApplicationContextAware {
    private final RouteLocatorBuilder routeLocatorBuilder;
    private final ApiRouteService apiRouteService;
    private final ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory;
    private final CacheService cacheService;
    private final MetricService metricService;
    private final ObjectMapper objectMapper;
    private final org.springframework.core.env.Environment environment;

    private ApplicationContext applicationContext;
    private Map<String, FilterService> filterServiceMap;

    @PostConstruct
    public void init() {
        boolean redisEnabled = environment.getProperty("app.redis.enabled", Boolean.class, true);
        this.filterServiceMap = Map.of(
                "CircuitBreaker", new CircuitBreakerFilterService(reactiveResilience4JCircuitBreakerFactory),
                "RedisRateLimiter",
                new RedisRateLimiterFilterService(applicationContext, cacheService, objectMapper, redisEnabled),
                "TimeLimiter", new TimeLimiterFilterService(),
                "Retry", new RetryFilterService());
    }

    @Override
    public Flux<Route> getRoutes() {
        RouteLocatorBuilder.Builder routesBuilder = routeLocatorBuilder.routes();
        // Use the internal method that returns ApiRoute entities for route building
        return apiRouteService.getAllRoutesInternal()
                .map(apiRoute -> routesBuilder.route(String.valueOf(apiRoute.getRouteIdentifier()),
                        predicateSpec -> setPredicateSpec(apiRoute, predicateSpec)))
                .collectList()
                .flatMapMany(builders -> routesBuilder.build()
                        .getRoutes());
    }

    private Buildable<Route> setPredicateSpec(ApiRoute apiRoute, PredicateSpec predicateSpec) {
        final BooleanSpec routeSpec;

        // Start with the first method and path
        if (!apiRoute.getMethods().isEmpty()) {
            routeSpec = predicateSpec
                    .method(apiRoute.getMethods().getFirst())
                    .and()
                    .path(apiRoute.getPath());

            // Add other methods as OR conditions
            for (int i = 1; i < apiRoute.getMethods().size(); i++) {
                routeSpec.or().method(apiRoute.getMethods().get(i))
                        .and()
                        .path(apiRoute.getPath());
            }
        } else {
            routeSpec = predicateSpec.path(apiRoute.getPath());
        }

        // Add body caching filter first
        routeSpec.filters(f -> f.filter((exchange, chain) -> {
            String method = exchange.getRequest().getMethod().name().toUpperCase();
            if (Arrays.asList("POST", "PUT", "PATCH").contains(method)) {
                return DataBufferUtils.join(exchange.getRequest().getBody())
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            String body = new String(bytes, StandardCharsets.UTF_8);
                            exchange.getAttributes().put("cachedRequestBody", body);

                            ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
                                    exchange.getRequest()) {
                                @Override
                                public @NonNull Flux<DataBuffer> getBody() {
                                    return Flux.just(exchange.getResponse().bufferFactory()
                                            .wrap(bytes));
                                }
                            };

                            return chain.filter(exchange.mutate().request(decorator).build());
                        });
            }
            return chain.filter(exchange);
        }));

        // Apply resilience filters for non-health endpoints
        applyFilters(routeSpec, apiRoute);

        // Note: Resilience filters are applied above, so they will execute for all
        // endpoints
        // Health check endpoints will still go through filters but be lightweight
        // To completely skip filters for health endpoints, they should be excluded at
        // route definition time

        // Add a custom filter to capture metrics only for real requests
        routeSpec.filters(f -> f.filter((exchange, chain) -> {
            long startTime = System.currentTimeMillis();
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        boolean success = Objects.requireNonNull(exchange.getResponse().getStatusCode())
                                .is2xxSuccessful();
                        captureMetricsForExchange(apiRoute, exchange, duration, success);
                    }));
        }));

        return routeSpec.uri(apiRoute.getUri());
    }

    private void captureMetricsForExchange(ApiRoute apiRoute, ServerWebExchange exchange, long duration,
            boolean success) {
        String pathEndpoint = exchange.getRequest().getURI().getPath();

        // Check for health endpoint BEFORE any processing
        if (pathEndpoint.endsWith("/health") || pathEndpoint.endsWith("/health/")) {
            return;
        }

        ApiMetric metric = new ApiMetric();
        metric.setRouteIdentifier(apiRoute.getRouteIdentifier());
        metric.setTimestamp(LocalDateTime.now());
        metric.setDuration(duration);
        metric.setSuccess(success);

        // Set toService
        // Check for custom service name header
        String fromService = determineInteractionType(exchange, metric);
        metric.setFromService(fromService);

        // Set toService
        String toService = extractServiceNameFromUri(apiRoute.getUri());
        metric.setToService(toService);

        // Set gatewayBaseUrl
        String gatewayBaseUrl = exchange.getRequest().getURI().getScheme() + "://" +
                exchange.getRequest().getURI().getHost() + ":" +
                exchange.getRequest().getURI().getPort();
        metric.setGatewayBaseUrl(gatewayBaseUrl);

        // Set pathEndpoint (the path part of the URL)
        metric.setPathEndPoint(pathEndpoint);

        // Set the HTTP method
        String httpMethod = exchange.getRequest().getMethod().name().toUpperCase();
        metric.setMethod(httpMethod);

        // Set queryParameters for methods that typically use query params
        if (Arrays.asList("GET", "DELETE", "HEAD", "OPTIONS").contains(httpMethod)) {
            String queryParameters = exchange.getRequest().getURI().getQuery();
            metric.setQueryParameters(queryParameters != null ? queryParameters : "");
        }

        // For methods with body, get the cached body if available
        if (Arrays.asList("POST", "PUT", "PATCH").contains(httpMethod)) {
            String cachedBody = exchange.getAttribute("cachedRequestBody");
            if (cachedBody != null) {
                metric.setRequestPayload(cachedBody);
            }
        }

        // Save metric for all methods
        metricService.saveMetric(metric).subscribe();
    }

    private String determineInteractionType(ServerWebExchange exchange, ApiMetric metric) {
        log.info("determineInteractionType Headers:");
        log.info(exchange.getRequest().getHeaders().toString());
        String fromService = exchange.getRequest().getHeaders().getFirst("X-Service-Name");
        if (fromService != null && !fromService.isEmpty()) {
            metric.setInteractionType("APP_TO_APP");
        } else {
            String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getHostName()
                    : "unknown";

            if ("0:0:0:0:0:0:0:1".equals(remoteAddress)) {
                remoteAddress = "127.0.0.1";
            }
            fromService = remoteAddress;
            metric.setInteractionType("USER_TO_APP");
        }
        return fromService;
    }

    private String extractServiceNameFromUri(String uri) {
        // Example URI: "lb://inventory-service" or "http://inventory-service"
        if (uri.startsWith("lb://") || uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri.split("://")[1].split("/")[0]; // Extract the service name
        }
        return uri; // Fallback in case the URI has an unexpected format
    }

    private void applyFilters(BooleanSpec booleanSpec, ApiRoute apiRoute) {
        List<FilterConfig> filters = apiRoute.getFilters();
        if (filters != null && !filters.isEmpty()) {
            booleanSpec.filters(gatewayFilterSpec -> {
                for (FilterConfig filter : filters) {
                    String filterName = filter.getName();

                    // Skip Redis and circuit breaker filters for health check endpoints
                    if (apiRoute.getHealthCheck() != null && apiRoute.getHealthCheck().isEnabled()) {
                        String healthPath = apiRoute.getHealthCheck().getPath();
                        if (healthPath != null && (filterName.equals("RedisRateLimiter") ||
                                filterName.equals("CircuitBreaker") ||
                                filterName.equals("TimeLimiter") ||
                                filterName.equals("Retry"))) {
                            log.debug("Skipping {} filter for health check path: {}", filterName, healthPath);
                            continue;
                        }
                    }

                    FilterService filterService = filterServiceMap.get(filterName);
                    if (filterService != null) {
                        try {
                            filterService.applyFilter(gatewayFilterSpec, filter, apiRoute);
                            log.debug("Applied filter {} for route {}", filterName, apiRoute.getRouteIdentifier());
                        } catch (Exception e) {
                            log.error("Error applying filter {} for route {}: {}", filterName,
                                    apiRoute.getRouteIdentifier(), e.getMessage());
                        }
                    } else {
                        log.warn("No filter service found for filter: {}", filterName);
                    }
                }
                return gatewayFilterSpec;
            });
        }
    }

    @Override
    public Flux<Route> getRoutesByMetadata(Map<String, Object> metadata) {
        return RouteLocator.super.getRoutesByMetadata(metadata);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
