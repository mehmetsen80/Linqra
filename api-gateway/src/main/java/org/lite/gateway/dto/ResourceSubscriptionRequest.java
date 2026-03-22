package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.ResourceSubscription;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceSubscriptionRequest {
    private String userId;
    private String resourceCategory;
    private String resourceId;
    private String appName;
    private ResourceSubscription.DeliveryConfig delivery;
}
