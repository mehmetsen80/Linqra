package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a notification sent to a user/team about a resource update.
 */
@Document(collection = "resource_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "sub_created_idx", def = "{'subscriptionId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "cat_res_idx", def = "{'resourceCategory': 1, 'resourceId': 1, 'createdAt': -1}")
})
public class ResourceUpdateNotification {
    @Id
    private String id;

    @Indexed
    private String subscriptionId; // Link to the specific ResourceSubscription

    private String resourceCategory; // e.g., "uscis-sentinel"
    private String resourceId; // e.g., "I-485"

    private String type; // e.g., "EDITION_UPDATE", "FEE_CHANGE"
    private String severity; // e.g., "HIGH", "MEDIUM", "LOW"

    private String summary; // Human-readable summary
    private String details; // Additional details (e.g., Markdown content)

    // Structured Delta
    private Map<String, Object> delta;

    @Indexed
    private boolean read;

    private LocalDateTime createdAt;
}
