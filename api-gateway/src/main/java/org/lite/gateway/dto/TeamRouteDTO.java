package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.FilterConfig;
import org.lite.gateway.entity.RoutePermission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRouteDTO {
    private String id;
    private TeamInfoDTO team;
    private String routeId;
    private String routeIdentifier;
    private String path;
    private Integer version;
    private Set<RoutePermission> permissions;
    private LocalDateTime assignedAt;
    private String assignedBy;
    private List<String> methods;
    private List<FilterConfig> filters;
    private String uri;
    private Integer maxCallsPerDay;
    private boolean healthCheckEnabled;
} 