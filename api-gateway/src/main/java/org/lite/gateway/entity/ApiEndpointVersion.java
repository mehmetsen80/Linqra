package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Data
@Builder
@Document("apiEndpointVersions")
@CompoundIndexes({
    // Primary: endpoint version lookup
    @CompoundIndex(name = "endpoint_version_idx", def = "{'endpointId': 1, 'version': -1}"),
    
    // Route-level queries
    @CompoundIndex(name = "route_version_idx", def = "{'routeIdentifier': 1, 'version': -1}")
})
public class ApiEndpointVersion {
    @Id
    private String id;
    private String endpointId;      // References the original endpoint
    private String routeIdentifier; // References the route this endpoint belongs to
    private Integer version;
    private ApiEndpoint endpointData;
    private Long createdAt;
} 