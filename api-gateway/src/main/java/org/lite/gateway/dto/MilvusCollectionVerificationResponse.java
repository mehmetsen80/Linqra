package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilvusCollectionVerificationResponse {
    private String name;
    private String teamId;
    private Integer vectorDimension;
    private String vectorFieldName;
    private Long rowCount;
    private String description;
    private boolean valid;
    private List<String> issues;
    private List<FieldInfo> schema;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldInfo {
        private String name;
        private String dataType;
        private boolean primary;
        private Map<String, String> typeParams;
        private Integer maxLength;
    }
}


