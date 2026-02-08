package org.lite.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.model.MetricPoint;
import org.lite.gateway.model.TrendAnalysis;
import org.lite.gateway.model.TrendDirection;
import org.springframework.stereotype.Service;

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

    public void addMetrics(String serviceId, Map<String, Double> newMetrics) {
        String redisKey = METRICS_KEY_PREFIX + serviceId;
        long timestamp = System.currentTimeMillis();

        newMetrics.forEach((newMetric, value) -> {
            try {
                // Get existing points
                String jsonPoints = cacheService.getHash(redisKey, newMetric).block();
                List<MetricPoint> points = jsonPoints != null
                        ? objectMapper.readValue(jsonPoints, new TypeReference<List<MetricPoint>>() {
                        })
                        : new ArrayList<>();

                // Add new point
                points.add(new MetricPoint(newMetric, value, timestamp));

                // Keep only the latest points
                if (points.size() > DEFAULT_MAX_HISTORY_SIZE) {
                    points = points.subList(points.size() - DEFAULT_MAX_HISTORY_SIZE, points.size());
                }

                // Store updated points
                String updatedJson = objectMapper.writeValueAsString(points);
                cacheService.putHash(redisKey, newMetric, updatedJson).block();
            } catch (JsonProcessingException e) {
                log.error("Error storing new metrics for service {} metric {}: {}",
                        serviceId, newMetric, e.getMessage());
            }
        });
    }

    public Map<String, TrendAnalysis> analyzeTrends(String serviceId) {
        Map<String, TrendAnalysis> trends = new HashMap<>();
        String redisKey = METRICS_KEY_PREFIX + serviceId;

        Map<String, String> entries = cacheService.getHashEntries(redisKey).block();
        if (entries != null) {
            entries.forEach((metric, jsonPoints) -> {
                try {
                    List<MetricPoint> points = objectMapper.readValue((String) jsonPoints,
                            new TypeReference<List<MetricPoint>>() {
                            });
                    trends.put((String) metric, calculateTrend(points));
                } catch (JsonProcessingException e) {
                    log.error("Error analyzing trends for service {} metric {}: {}",
                            serviceId, metric, e.getMessage());
                }
            });
        }
        return trends;
    }

    private TrendAnalysis calculateTrend(List<MetricPoint> points) {
        if (points.size() < 2)
            return new TrendAnalysis(0.0, TrendDirection.STABLE);

        MetricPoint recentPoint = points.getLast();
        MetricPoint previousPoint = points.get(points.size() - 2);
        double recent = recentPoint.getValue();
        double previous = previousPoint.getValue();
        return TrendAnalysis.fromValues(recent, previous);
    }

    public Map<String, List<MetricPoint>> getMetricsHistory(String serviceId) {
        Map<String, List<MetricPoint>> history = new HashMap<>();
        String redisKey = METRICS_KEY_PREFIX + serviceId;

        Map<String, String> entries = cacheService.getHashEntries(redisKey).block();
        if (entries != null) {
            entries.forEach((metric, jsonPoints) -> {
                try {
                    List<MetricPoint> points = objectMapper.readValue((String) jsonPoints,
                            new TypeReference<List<MetricPoint>>() {
                            });
                    history.put((String) metric, points);
                } catch (JsonProcessingException e) {
                    log.error("Error retrieving metrics history for service {} metric {}: {}",
                            serviceId, metric, e.getMessage());
                }
            });
        }
        return history;
    }

    public void storeTrendAnalysis(String serviceId, Map<String, TrendAnalysis> trends) {
        try {
            String redisKey = METRICS_KEY_PREFIX + serviceId + ":trends";
            String jsonTrends = objectMapper.writeValueAsString(trends);
            cacheService.set(redisKey, jsonTrends, java.time.Duration.ofDays(30)).block(); // Use reasonable expiration
        } catch (JsonProcessingException e) {
            log.error("Error storing trend analysis for service {}: {}", serviceId, e.getMessage());
        }
    }

    public Map<String, Double> getCurrentMetrics(String serviceId) {
        return getMetricsHistory(serviceId).entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get(e.getValue().size() - 1).getValue()));
    }
}