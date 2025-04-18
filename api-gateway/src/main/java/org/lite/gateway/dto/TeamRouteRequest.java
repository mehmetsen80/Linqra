package org.lite.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.lite.gateway.entity.RoutePermission;

import java.util.Set;

@Data
public class TeamRouteRequest {
    @NotBlank(message = "Team ID is required")
    private String teamId;
    
    @NotBlank(message = "Route ID is required")
    private String routeId;
    
    @NotNull(message = "Permissions are required")
    private Set<RoutePermission> permissions;
} 