package org.lite.gateway.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwaggerParameter {
    private String name;
    private String in;
    private String description;
    private boolean required;
    private Map<String, Object> schema;
} 