package org.lite.gateway.controller;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.model.DashboardUpdate;
import org.lite.gateway.model.TrendAnalysis;
import org.lite.gateway.service.HealthCheckService;
import org.lite.gateway.service.MetricsAggregator;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.BrokerAvailabilityEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/health")
@Slf4j
public class HealthCheckServiceController {
    private final HealthCheckService healthCheckService;
    private final MetricsAggregator metricsAggregator;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean hasSubscribers = new AtomicBoolean(true);

    public HealthCheckServiceController(
            SimpMessagingTemplate simpMessagingTemplate,
            HealthCheckService healthCheckService,
            MetricsAggregator metricsAggregator) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.healthCheckService = healthCheckService;
        this.metricsAggregator = metricsAggregator;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        startCheckUpdates();
    }

    // Send updates every 5 seconds
    private void startCheckUpdates() {
        scheduler.scheduleAtFixedRate(this::sendHealthUpdate, 0, 10, TimeUnit.SECONDS);
    }

    private void sendHealthUpdate() {
        healthCheckService.getHealthCheckEnabledRoutes()
                .flatMap(route -> healthCheckService.getServiceStatus(route.getRouteIdentifier())
                        .map(status -> {
                            log.debug("Service {} status: {}", route.getRouteIdentifier(), status.getStatus());
                            return new DashboardUpdate(
                                    route.getRouteIdentifier(),
                                    status,
                                    metricsAggregator.analyzeTrends(route.getRouteIdentifier()));
                        })
                        .onErrorResume(e -> {
                            log.error("Error getting status for {}: {}", route.getRouteIdentifier(), e.getMessage());
                            return Mono.empty();
                        }))
                .collectList()
                .doOnNext(updates -> {
                    if (updates.isEmpty()) {
                        log.warn("No service updates available - all services might be down");
                    } else {
                        log.info("Collected updates for {} services", updates.size());
                    }
                })
                .subscribe(this::sendHealthUpdate);
    }

    private void sendHealthUpdate(List<DashboardUpdate> payload) {
        if (payload.isEmpty()) {
            log.warn("No health updates to send - services might be down");
            return;
        }

        // Log each service's status
        payload.forEach(update -> {
            String serviceId = update.getServiceId();
            String status = update.getStatus().getStatus();
            boolean healthy = update.getStatus().isHealthy();
            log.info("Service: {}, Status: {}, Healthy: {}", serviceId, status, healthy);
        });

        try {
            log.info("Sending health updates for {} services", payload.size());
            simpMessagingTemplate.convertAndSend("/topic/health", payload);
            log.debug("Health updates sent successfully");
        } catch (MessageDeliveryException e) {
            log.warn("Failed to send health updates: {}", e.getMessage());
            hasSubscribers.set(false);
        }
    }

    // Triggered when a client subscribes
    @MessageMapping("/subscribe")
    public void handleSubscription() {
        log.info("New client subscription received");
        hasSubscribers.set(true);
        sendHealthUpdate();
    }

    // Listen for broker availability events
    @EventListener
    public void onBrokerAvailabilityEvent(BrokerAvailabilityEvent event) {
        if (!event.isBrokerAvailable()) {
            log.warn("Broker became unavailable");
            hasSubscribers.set(false);
        } else {
            log.info("Broker is available");
            hasSubscribers.set(true);
        }
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdown();
    }

    @GetMapping("/data/{serviceId}")
    public Mono<Map<String, Object>> getHealthData(@PathVariable String serviceId) {
        Map<String, Double> metrics = metricsAggregator.getCurrentMetrics(serviceId);

        // Get service health status
        Mono<Boolean> healthStatus = healthCheckService.isServiceHealthy(serviceId);

        // Get trends analysis
        Map<String, TrendAnalysis> trends = healthCheckService.analyzeServiceTrends(serviceId);

        // Combine all data
        return healthStatus.map(isHealthy -> {
            Map<String, Object> dashboardData = new HashMap<>();
            dashboardData.put("serviceId", serviceId);
            dashboardData.put("healthy", isHealthy);
            dashboardData.put("metrics", metrics);
            dashboardData.put("trends", trends);
            return dashboardData;
        });
    }
}