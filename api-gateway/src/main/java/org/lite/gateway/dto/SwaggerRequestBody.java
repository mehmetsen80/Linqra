package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SwaggerRequestBody {
    private Map<String, SwaggerMediaType> content;
    private boolean required;
} 