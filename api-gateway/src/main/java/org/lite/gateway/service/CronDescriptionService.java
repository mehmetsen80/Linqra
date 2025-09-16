package org.lite.gateway.service;

public interface CronDescriptionService {
    
    /**
     * Parse a cron expression and generate a human-readable description
     * @param cronExpression The cron expression to parse (must be 6 fields)
     * @return Human-readable description of the cron schedule
     * @throws IllegalArgumentException if cron expression is invalid
     */
    String getCronDescription(String cronExpression);
} 