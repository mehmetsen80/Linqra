package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a user or team subscription to a specific resource (e.g.,
 * USCIS Form I-485).
 */
@Document(collection = "resource_subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "user_resource_idx", def = "{'userId': 1, 'domain': 1, 'category': 1, 'resourceId': 1, 'appName': 1}", unique = true),
        @CompoundIndex(name = "team_resource_idx", def = "{'teamId': 1, 'domain': 1, 'category': 1, 'resourceId': 1, 'appName': 1}"),
        @CompoundIndex(name = "resource_enabled_idx", def = "{'domain': 1, 'category': 1, 'resourceId': 1, 'enabled': 1}")
})
public class ResourceSubscription {
    @Id
    private String id;

    private String userId; // Optional: Personal subscription
    private String teamId; // Optional: Team-wide subscription

    private String domain; // e.g., "uscis-sentinel"
    private String category; // e.g., "forms", "news", "alerts"
    private String resourceId; // Mandatory: e.g., "I-485"
    private String appName; // e.g., "komunas-app"

    @Builder.Default
    private boolean enabled = true;

    // Delivery settings
    private DeliveryConfig delivery;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeliveryConfig {
        private boolean emailEnabled;
        private String email; // Target email for notifications
        private boolean webhookEnabled;
        private String webhookUrl;
    }

    // Filter settings (if still needed, though resourceId is now primary)
    private Map<String, Object> filters;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
