package org.lite.gateway.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "alerts")
@Data
@CompoundIndexes({
    // Primary: active alerts by route
    @CompoundIndex(name = "route_active_idx", def = "{'routeId': 1, 'active': 1}"),
    
    // Alert lookup by metric
    @CompoundIndex(name = "route_metric_active_idx", def = "{'routeId': 1, 'metric': 1, 'active': 1}"),
    
    // Exact match query (avoid duplicates)
    @CompoundIndex(name = "alert_unique_idx", def = "{'routeId': 1, 'metric': 1, 'condition': 1, 'threshold': 1}")
})
public class Alert {
    @Id
    private String id;
    private String routeId;
    private String metric;
    private String condition;
    private double threshold;
    private LocalDateTime createdAt;
    private boolean active;
    private int consecutiveFailures;
    private Map<String, Double> lastMetrics;
    private String lastErrorMessage;
    private LocalDateTime lastUpdated;
    private String severity;

    public static Alert fromRule(AlertRule rule, String routeId, Map<String, Double> metrics) {
        Alert alert = new Alert();
        alert.setRouteId(routeId);
        alert.setMetric(rule.getMetric());
        alert.setCondition(rule.getCondition());
        alert.setThreshold(rule.getThreshold());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setLastUpdated(LocalDateTime.now());
        alert.setActive(true);
        alert.setLastMetrics(metrics);
        alert.setSeverity(rule.getSeverity().toString());
        
        Double value = metrics.get(rule.getMetric());
        alert.setLastErrorMessage(String.format("%s usage (%.1f%%) exceeded threshold (%.1f%%)", 
            rule.getMetric(), value, rule.getThreshold()));
        
        return alert;
    }
} 