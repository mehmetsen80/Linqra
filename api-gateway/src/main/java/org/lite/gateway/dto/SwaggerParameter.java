package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerParameter {
    private String name;
    private String in;
    private String description;
    private boolean required;
    private Map<String, Object> schema;
} 