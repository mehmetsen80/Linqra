package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@Builder
@Document("apiEndpointVersionMetadata")
public class ApiEndpointVersionMetadata {
    @Id
    private String id;
    private String endpointId;
    private String routeIdentifier;
    private Integer version;
    private ChangeType changeType;
    private String changeReason;
    private String changeDescription;
    private Long createdAt;
    private Map<String, Object> changedFields;
    private String changedBy;

    public enum ChangeType {
        CREATE,
        UPDATE,
        ROLLBACK,
        SWAGGER_UPDATE
    }
} 