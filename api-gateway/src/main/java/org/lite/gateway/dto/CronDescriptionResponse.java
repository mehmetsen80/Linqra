package org.lite.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for cron description
 */
@Setter
@Getter
@Schema(description = "Cron description response")
public class CronDescriptionResponse {
    
    @Schema(description = "The original cron expression", example = "0 0 9 * * *")
    private String cronExpression;
    
    @Schema(description = "Human-readable description of the cron expression", example = "Every day at 9:00 AM")
    private String description;

    public CronDescriptionResponse() {}

    public CronDescriptionResponse(String cronExpression, String description) {
        this.cronExpression = cronExpression;
        this.description = description;
    }

}