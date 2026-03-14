package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceNotificationDispatchDTO {
    private String resourceCategory;
    private String resourceId;
    private String type;
    private String severity;
    private String summary;
    private String details;
    private Map<String, Object> delta;
}
