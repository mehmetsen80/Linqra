package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerProperty {
    private String type;
    private String format;
    private Map<String, Object> additionalProperties;
    private String reference;
} 