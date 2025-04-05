package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document("apiEndpointVersions")
public class ApiEndpointVersion {
    @Id
    private String id;
    private String endpointId;      // References the original endpoint
    private String routeIdentifier; // References the route this endpoint belongs to
    private Integer version;
    private ApiEndpoint endpointData;
    private Long createdAt;
} 