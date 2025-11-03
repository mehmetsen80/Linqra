package org.lite.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkUploadInitiateRequest {
    
    @NotEmpty(message = "At least one file is required")
    private List<@Valid UploadInitiateRequest> files;
}

