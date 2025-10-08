package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.FilterConfig;
import org.lite.gateway.entity.HealthCheckConfig;

import java.util.List;

/**
 * DTO for ApiRoute with additional assignment information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiRouteDTO {
    private String id;
    private Integer version;
    private Long createdAt;
    private Long updatedAt;
    private String routeIdentifier;
    private String uri;
    private List<String> methods;
    private String path;
    private String scope;
    private Integer maxCallsPerDay;
    private List<FilterConfig> filters;
    private HealthCheckConfig healthCheck;
    private String teamId;
    
    // Assignment information from TeamRoute
    private String assignedBy;
}

