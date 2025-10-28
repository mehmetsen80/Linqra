package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinqLlmModelDTO {
    private String id;
    private String modelCategory;
    private String modelName;
    private String endpoint;
    private String method;
    private Map<String, String> headers;
    private String authType;
    private String apiKey;
    private List<String> supportedIntents;
    private String team;
}

