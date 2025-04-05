package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SwaggerEndpointInfo {
    private String path;
    private String method;
    private String summary;
    private String operationId;
    private List<SwaggerParameter> parameters;
    private SwaggerRequestBody requestBody;
    private Map<String, SwaggerResponse> responses;
    private List<String> tags;
} 