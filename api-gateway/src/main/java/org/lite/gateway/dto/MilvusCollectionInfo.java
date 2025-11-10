package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilvusCollectionInfo {
    private String name;
    private String teamId;
    private Integer vectorDimension;
    private String vectorFieldName;
    private String description;
    private String collectionType;
    private Map<String, String> properties;
    private boolean nameLocked;
    private Long rowCount;
}