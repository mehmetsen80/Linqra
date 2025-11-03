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
public class BulkUploadResponse {
    private List<UploadResponse> uploads;
    private Integer successCount;
    private Integer failureCount;
    private String message;
    private String error;
    
    public static BulkUploadResponse error(String errorMessage) {
        return BulkUploadResponse.builder()
            .error(errorMessage)
            .failureCount(0)
            .successCount(0)
            .message("Bulk upload failed")
            .build();
    }
}

