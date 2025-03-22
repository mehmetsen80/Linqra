package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EndpointId {
    private String service;
    private String path;
    private String method;
} 