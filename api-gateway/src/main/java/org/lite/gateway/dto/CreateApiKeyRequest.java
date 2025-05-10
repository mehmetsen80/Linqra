package org.lite.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiKeyRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotBlank(message = "Team ID is required")
    private String teamId;

    private String createdBy;
    
    // Optional: if you want to support custom expiration
    private Long expiresInDays;
} 