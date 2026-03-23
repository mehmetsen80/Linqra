package org.lite.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.EurekaClient;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ServiceDTO;
import org.lite.gateway.entity.ApiRoute;
import org.lite.gateway.entity.HealthThresholds;
import org.lite.gateway.model.ServiceHealthStatus;
import org.lite.gateway.model.TrendAnalysis;
import org.lite.gateway.repository.ApiRouteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Validated
@Slf4j
public class HealthCheckService {

    @Value("${server.port:7777}")
    private int serverPort;
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;
    @Value("${eureka.instance.hostname:localhost}")
    private String hostname;

    private final ApiRouteRepository apiRouteRepository;
    private final WebClient.Builder webClientBuilder;
    private final MetricsAggregator metricsAggregator;
    private final AlertService alertService;
    private final EurekaClient eurekaClient;
    private final org.springframework.core.env.Environment env;
    private final Map<String, ServiceDTO> serviceHealthCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public HealthCheckService(
            ApiRouteRepository apiRouteRepository,
            WebClient.Builder webClientBuilder,
            MetricsAggregator metricsAggregator,
            AlertService alertService,
            EurekaClient eurekaClient,
            org.springframework.core.env.Environment env,
            ObjectMapper objectMapper) {
        this.apiRouteRepository = apiRouteRepository;
        this.webClientBuilder = webClientBuilder;
        this.metricsAggregator = metricsAggregator;
        this.alertService = alertService;
        this.eurekaClient = eurekaClient;
        this.env = env;
        this.objectMapper = objectMapper;
    }

    public Flux<ApiRoute> getHealthCheckEnabledRoutes() {
        return apiRouteRepository.findAllWithHealthCheckEnabled();
    }

    public Mono<Boolean> isServiceHealthy(String routeId) {
        return apiRouteRepository.findByRouteIdentifier(routeId)
                .filter(route -> route.getHealthCheck().isEnabled())
                .flatMap(this::checkHealth)
                .map(healthData -> "UP".equals(healthData.getStatus()))
                .defaultIfEmpty(false);
    }

    public Mono<ServiceHealthStatus> getServiceStatus(String serviceId) {
        return apiRouteRepository.findByRouteIdentifier(serviceId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Service not found: " + serviceId)))
                .flatMap(this::checkHealth);
    }

    private Duration parseDuration(String uptimeStr) {
        try {
            if (uptimeStr == null) return Duration.ZERO;
            // Format is "0d 0h 0m 46s"
            String[] parts = uptimeStr.split(" ");
            long days = Long.parseLong(parts[0].replace("d", ""));
            long hours = Long.parseLong(parts[1].replace("h", ""));
            long minutes = Long.parseLong(parts[2].replace("m", ""));
            long seconds = Long.parseLong(parts[3].replace("s", ""));

            return Duration.ofDays(days)
                    .plusHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds);
        } catch (Exception e) {
            log.warn("Failed to parse uptime string: {}", uptimeStr);
            return Duration.ZERO;
        }
    }

    public boolean evaluateMetrics(Map<String, Double> metrics, HealthThresholds thresholds) {
        if (metrics == null || thresholds == null) {
            return false;
        }

        Double cpu = metrics.get("cpu");
        Double memory = metrics.get("memory");
        Double responseTime = metrics.get("responseTime");

        return (cpu == null || cpu <= thresholds.getCpuThreshold()) &&
                (memory == null || memory <= thresholds.getMemoryThreshold()) &&
                (responseTime == null || responseTime <= thresholds.getResponseTimeThreshold());
    }

    private Mono<ServiceHealthStatus> createServiceStatus(String routeId, Map<String, Object> healthData) {
        String serviceId = (String) healthData.get("serviceId");

        // Validate that the service IDs match
        if (serviceId != null && !routeId.equals(serviceId)) {
            log.error("Service ID mismatch. Route ID: {}, Health Response Service ID: {}", routeId, serviceId);
            return Mono.error(new RuntimeException("Service ID mismatch in health check response"));
        }

        boolean isUp = "UP".equals(healthData.get("status"));

        ServiceHealthStatus status = ServiceHealthStatus.builder()
                .serviceId(routeId)
                .healthy(isUp)
                .status((String) healthData.get("status"))
                .metrics(extractMetrics(healthData))
                .uptime(parseDuration((String) healthData.get("uptime")))
                .lastChecked(System.currentTimeMillis())
                .build();

        // Add new metrics to Redis - Non-blocking
        if (status.getMetrics() != null) {
            return metricsAggregator.addMetrics(routeId, status.getMetrics())
                    .thenReturn(status);
        }

        return Mono.just(status);
    }

    private Map<String, Double> extractMetrics(Map<String, Object> healthData) {
        Map<String, Double> metrics = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> metricsData = (Map<String, Object>) healthData.get("metrics");

        if (metricsData != null) {
            metricsData.forEach((key, value) -> {
                if (value instanceof Number) {
                    metrics.put(key, ((Number) value).doubleValue());
                }
            });
        }

        return metrics;
    }

    public Mono<Map<String, TrendAnalysis>> analyzeServiceTrends(String serviceId) {
        return metricsAggregator.analyzeTrends(serviceId);
    }

    @SuppressWarnings("unchecked")
    private Mono<ServiceHealthStatus> checkHealth(ApiRoute route) {
        String serviceId = route.getRouteIdentifier();

        return isServiceRegistered(serviceId)
                .flatMap(registered -> {
                    if (!registered) {
                        log.debug("Service {} is not registered with Eureka, skipping health check", serviceId);
                        return Mono.empty();
                    }

                    // REMOTE-DEV Specific: Skip health checks for remote services (non '-dev') to avoid noise
                    if (env.matchesProfiles("remote-dev") && !serviceId.toLowerCase().endsWith("-dev")) {
                        log.debug("Skipping health check for non-dev service {} in remote-dev profile", serviceId);
                        return Mono.empty();
                    }

                    String healthEndpoint = getHealthEndpoint(route);
                    long startTime = System.currentTimeMillis();

                    return createWebClient()
                            .get()
                            .uri(healthEndpoint)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(responseBody -> {
                                try {
                                    Map<String, Object> healthData = objectMapper.readValue(responseBody, Map.class);
                                    long responseTime = System.currentTimeMillis() - startTime;

                                    Map<String, Object> metrics = (Map<String, Object>) healthData.getOrDefault("metrics",
                                            new HashMap<>());
                                    healthData.put("metrics", metrics);
                                    metrics.put("responseTime", (double) responseTime);

                                    return createServiceStatus(serviceId, healthData)
                                            .flatMap(status -> processHealthStatus(route, status)
                                                    .thenReturn(status));
                                } catch (Exception e) {
                                    log.error("Failed to parse health check response for service {}: {}", serviceId,
                                            e.getMessage());
                                    return Mono.error(new RuntimeException("Failed to parse health check response", e));
                                }
                            })
                            .onErrorResume(e -> {
                                if (env.matchesProfiles("remote-dev")) {
                                    return Mono.empty();
                                }
                                log.error("Health check failed for service {}: {}", serviceId, e.getMessage());
                                return handleHealthCheckError(serviceId, e);
                            });
                });
    }

    private WebClient createWebClient() {
        if (sslEnabled && env.matchesProfiles("remote-dev")) {
            try {
                io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                        .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                        .build();

                reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                        .secure(t -> t.sslContext(sslContext));

                return WebClient.builder()
                        .clientConnector(
                                new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                        .build();
            } catch (Exception e) {
                log.error("Could not create insecure WebClient for remote-dev", e);
            }
        }
        return webClientBuilder.build();
    }

    private Mono<Void> processHealthStatus(ApiRoute route, ServiceHealthStatus status) {
        String serviceId = route.getRouteIdentifier();

        return metricsAggregator.analyzeTrends(serviceId)
                .flatMap(trends -> {
                    Mono<Void> alertMono;
                    if (!status.isHealthy() || !evaluateMetrics(status.getMetrics(), route.getHealthCheck().getThresholds())) {
                        status.incrementConsecutiveFailures();
                        alertMono = alertService.processHealthStatus(
                                serviceId,
                                false,
                                status.getMetrics(),
                                status.getConsecutiveFailures(),
                                route.getHealthCheck().getAlertRules());
                    } else {
                        status.resetConsecutiveFailures();
                        alertMono = alertService.resolveHealthAlerts(serviceId);
                    }

                    Mono<Void> trendMono = trends.isEmpty() ? Mono.empty()
                            : metricsAggregator.storeTrendAnalysis(serviceId, trends);

                    return Mono.when(alertMono, trendMono);
                });
    }

    private Mono<ServiceHealthStatus> handleHealthCheckError(String serviceId, Throwable error) {
        ServiceHealthStatus errorStatus = ServiceHealthStatus.builder()
                .healthy(false)
                .status("DOWN")
                .lastChecked(System.currentTimeMillis())
                .build();

        // Store error status in Redis
        return metricsAggregator.addMetrics(serviceId, Map.of("error", 1.0))
                .thenReturn(errorStatus);
    }

    public Flux<ServiceHealthStatus> getAllServicesStatus() {
        return apiRouteRepository.findAllWithHealthCheckEnabled()
                .flatMap(route -> checkHealth(route)
                        .onErrorResume(e -> {
                            log.error("Error checking health for service {}: {}",
                                    route.getRouteIdentifier(), e.getMessage());
                            return handleHealthCheckError(route.getRouteIdentifier(), e);
                        }));
    }

    private String getHealthEndpoint(ApiRoute route) {
        String protocol = sslEnabled ? "https" : "http";
        String basePath = route.getPath().replaceAll("/\\*\\*", "");
        String healthPath = route.getHealthCheck().getPath();
        return String.format("%s://%s:%d%s%s",
                protocol, hostname, serverPort, basePath, healthPath);
    }

    // Wrap blocking Eureka call to run on bounded elastic scheduler
    private Mono<Boolean> isServiceRegistered(String serviceId) {
        return Mono.fromCallable(() -> {
            try {
                com.netflix.discovery.shared.Application application = eurekaClient.getApplication(serviceId);
                return application != null && !application.getInstances().isEmpty();
            } catch (Exception e) {
                log.debug("Error checking service registration for {}: {}", serviceId, e.getMessage());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Optional<ServiceDTO> getServiceHealth(String serviceId) {
        return Optional.ofNullable(serviceHealthCache.get(serviceId));
    }
}