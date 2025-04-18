package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwaggerMediaType {
    private Map<String, Object> schema;
    private Object example;
} 