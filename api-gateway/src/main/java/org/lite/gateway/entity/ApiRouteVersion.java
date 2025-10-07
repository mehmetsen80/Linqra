package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Data
@Builder
@Document("apiRouteVersions")
@CompoundIndexes({
    // Primary query: get versions by routeIdentifier, sorted by version DESC
    @CompoundIndex(name = "route_version_idx", def = "{'routeIdentifier': 1, 'version': -1}"),
    
    // Secondary query: get versions by routeId (for internal lookups)
    @CompoundIndex(name = "routeId_version_idx", def = "{'routeId': 1, 'version': -1}"),
    
    // Temporal query: get recent versions by creation time
    @CompoundIndex(name = "route_created_idx", def = "{'routeIdentifier': 1, 'createdAt': -1}")
})
public class ApiRouteVersion {
    @Id
    private String id;
    private String routeId;
    private String routeIdentifier;
    private Integer version;
    private ApiRoute routeData;
    private Long createdAt;
} 