package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlIngressionRequest {
    private String url;
    private String fileName;
    private String collectionId;
    private String teamId;
    private String contentType;
}
