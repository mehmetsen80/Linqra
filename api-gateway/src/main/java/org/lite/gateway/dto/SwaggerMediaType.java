package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerMediaType {
    private Map<String, Object> schema;
} 