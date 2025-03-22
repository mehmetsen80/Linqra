package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document("apiRouteVersions")
public class ApiRouteVersion {
    @Id
    private String id;
    private String routeId;
    private String routeIdentifier;
    private Integer version;
    private ApiRoute routeData;
    private Long createdAt;
} 