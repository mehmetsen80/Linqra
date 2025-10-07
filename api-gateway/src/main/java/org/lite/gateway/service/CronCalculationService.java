package org.lite.gateway.service;

import java.time.LocalDateTime;

/**
 * Service for calculating cron-related timing information
 */
public interface CronCalculationService {
    
    /**
     * Calculate the next run time for a given cron expression
     * @param cronExpression the cron expression (Quartz format)
     * @return the next scheduled run time, or null if invalid
     */
    LocalDateTime calculateNextRun(String cronExpression);
    
    /**
     * Calculate the next run time after a specific date
     * @param cronExpression the cron expression (Quartz format)
     * @param after the date to calculate after
     * @return the next scheduled run time, or null if invalid
     */
    LocalDateTime calculateNextRunAfter(String cronExpression, LocalDateTime after);
}

