package org.lite.gateway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadInitiateRequest {
    
    @NotBlank(message = "File name is required")
    private String fileName;
    
    @NotBlank(message = "Collection ID is required")
    private String collectionId;
    
    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    private Long fileSize;
    
    @NotBlank(message = "Content type is required")
    private String contentType;
    
    // Optional processing options
    private Integer chunkSize = 400;
    private Integer overlapTokens = 50;
    private String chunkStrategy = "sentence"; // sentence, paragraph, token
}

