package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDeltaAnalysisRequest {
    private String oldDocumentId;
    private String newDocumentId;
    private String resourceId;
    private String resourceCategory;
    private List<String> categories;
}
