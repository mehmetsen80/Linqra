package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "team_routes")
@CompoundIndexes({
    // Primary: team's routes (unique constraint)
    @CompoundIndex(name = "team_route_idx", def = "{'teamId': 1, 'routeId': 1}", unique = true),
    
    // Reverse: routes by team
    @CompoundIndex(name = "route_team_idx", def = "{'routeId': 1, 'teamId': 1}")
})
public class TeamRoute {
    @Id
    private String id;
    
    private String teamId;
    private String routeId;
    
    @Builder.Default
    private Set<RoutePermission> permissions = Set.of(RoutePermission.VIEW);
    
    private LocalDateTime assignedAt;
    private String assignedBy;
} 