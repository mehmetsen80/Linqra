package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrl {
    private String uploadUrl;
    private String s3Key;
    private Instant expiresAt;
    private Map<String, String> requiredHeaders;
    
    public Long getExpiresInSeconds() {
        if (expiresAt != null) {
            return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        }
        return null;
    }
}

