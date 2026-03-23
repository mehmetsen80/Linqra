package org.lite.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.model.MetricPoint;
import org.lite.gateway.model.TrendAnalysis;
import org.lite.gateway.model.TrendDirection;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MetricsAggregator {
    private static final int DEFAULT_MAX_HISTORY_SIZE = 100;
    private static final String METRICS_KEY_PREFIX = "metrics:";

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public MetricsAggregator(
            CacheService cacheService,
            ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> addMetrics(String serviceId, Map<String, Double> newMetrics) {
        if (newMetrics == null || newMetrics.isEmpty()) {
            return Mono.empty();
        }

        String redisKey = METRICS_KEY_PREFIX + serviceId;
        long timestamp = System.currentTimeMillis();

        return Flux.fromIterable(newMetrics.entrySet())
                .flatMap(entry -> {
                    String newMetric = entry.getKey();
                    Double value = entry.getValue();

                    return cacheService.getHash(redisKey, newMetric)
                            .defaultIfEmpty("[]")
                            .flatMap(jsonPoints -> {
                                try {
                                    List<MetricPoint> points = objectMapper.readValue(jsonPoints,
                                            new TypeReference<List<MetricPoint>>() {
                                            });
                                    if (points == null) {
                                        points = new ArrayList<>();
                                    }

                                    // Add new point
                                    points.add(new MetricPoint(newMetric, value, timestamp));

                                    // Keep only the latest points
                                    if (points.size() > DEFAULT_MAX_HISTORY_SIZE) {
                                        points = points.subList(points.size() - DEFAULT_MAX_HISTORY_SIZE, points.size());
                                    }

                                    // Store updated points
                                    String updatedJson = objectMapper.writeValueAsString(points);
                                    return cacheService.putHash(redisKey, newMetric, updatedJson);
                                } catch (JsonProcessingException e) {
                                    log.error("Error storing new metrics for service {} metric {}: {}",
                                            serviceId, newMetric, e.getMessage());
                                    return Mono.empty();
                                }
                            });
                })
                .then();
    }

    public Mono<Map<String, TrendAnalysis>> analyzeTrends(String serviceId) {
        String redisKey = METRICS_KEY_PREFIX + serviceId;

        return cacheService.getHashEntries(redisKey)
                .map(entries -> {
                    Map<String, TrendAnalysis> trends = new HashMap<>();
                    entries.forEach((metric, jsonPoints) -> {
                        try {
                            List<MetricPoint> points = objectMapper.readValue(jsonPoints,
                                    new TypeReference<List<MetricPoint>>() {
                                    });
                            trends.put(metric, calculateTrend(points));
                        } catch (JsonProcessingException e) {
                            log.error("Error analyzing trends for service {} metric {}: {}",
                                    serviceId, metric, e.getMessage());
                        }
                    });
                    return trends;
                })
                .defaultIfEmpty(new HashMap<>());
    }

    private TrendAnalysis calculateTrend(List<MetricPoint> points) {
        if (points == null || points.size() < 2)
            return new TrendAnalysis(0.0, TrendDirection.STABLE);

        MetricPoint recentPoint = points.get(points.size() - 1);
        MetricPoint previousPoint = points.get(points.size() - 2);
        double recent = recentPoint.getValue();
        double previous = previousPoint.getValue();
        return TrendAnalysis.fromValues(recent, previous);
    }

    public Mono<Map<String, List<MetricPoint>>> getMetricsHistory(String serviceId) {
        String redisKey = METRICS_KEY_PREFIX + serviceId;

        return cacheService.getHashEntries(redisKey)
                .map(entries -> {
                    Map<String, List<MetricPoint>> history = new HashMap<>();
                    entries.forEach((metric, jsonPoints) -> {
                        try {
                            List<MetricPoint> points = objectMapper.readValue(jsonPoints,
                                    new TypeReference<List<MetricPoint>>() {
                                    });
                            history.put(metric, points);
                        } catch (JsonProcessingException e) {
                            log.error("Error retrieving metrics history for service {} metric {}: {}",
                                    serviceId, metric, e.getMessage());
                        }
                    });
                    return history;
                })
                .defaultIfEmpty(new HashMap<>());
    }

    public Mono<Void> storeTrendAnalysis(String serviceId, Map<String, TrendAnalysis> trends) {
        try {
            String redisKey = METRICS_KEY_PREFIX + serviceId + ":trends";
            String jsonTrends = objectMapper.writeValueAsString(trends);
            return cacheService.set(redisKey, jsonTrends, java.time.Duration.ofDays(30));
        } catch (JsonProcessingException e) {
            log.error("Error storing trend analysis for service {}: {}", serviceId, e.getMessage());
            return Mono.empty();
        }
    }

    public Mono<Map<String, Double>> getCurrentMetrics(String serviceId) {
        return getMetricsHistory(serviceId)
                .map(history -> history.entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().get(e.getValue().size() - 1).getValue())))
                .defaultIfEmpty(new HashMap<>());
    }
}