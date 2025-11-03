package org.lite.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UploadCompleteRequest {
    
    @NotBlank(message = "S3 key is required")
    private String s3Key;
}

