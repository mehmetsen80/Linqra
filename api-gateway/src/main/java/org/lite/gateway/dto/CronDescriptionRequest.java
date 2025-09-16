package org.lite.gateway.dto;

/**
 * Request DTO for cron description
 */
public class CronDescriptionRequest {
    private String cronExpression;

    public CronDescriptionRequest() {}

    public CronDescriptionRequest(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }
} 