package org.lite.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class VersionComparisonResult {
    private String routeIdentifier;
    private Integer version1;
    private Integer version2;
    private Map<String, FieldDifference> differences;
    private List<String> addedFields;
    private List<String> removedFields;

    @Data
    @Builder
    public static class FieldDifference {
        private Object oldValue;
        private Object newValue;
        private String fieldPath;
    }
} 