package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerResponse {
    private String description;
    private Map<String, SwaggerMediaType> content;
} 