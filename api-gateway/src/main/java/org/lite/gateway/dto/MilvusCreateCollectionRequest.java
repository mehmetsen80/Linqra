package org.lite.gateway.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MilvusCreateCollectionRequest {
    private String collectionName;
    private List<Map<String, Object>> schemaFields;
    private String description;
    private String teamId;
    private String collectionType;
    private Map<String, String> properties;
}