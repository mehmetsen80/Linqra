package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerSchemaInfo {
    private String name;
    private String type;
    private Map<String, SwaggerProperty> properties;
} 