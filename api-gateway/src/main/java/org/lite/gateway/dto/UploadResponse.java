package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String documentId;
    private String uploadUrl;
    private String s3Key;
    private Long expiresInSeconds;
    private Map<String, String> requiredHeaders;
    private String instructions;
    private String status;
    private String message;
    private String error;
    
    public static UploadResponse error(String errorMessage) {
        return UploadResponse.builder()
            .error(errorMessage)
            .status("ERROR")
            .build();
    }
}

