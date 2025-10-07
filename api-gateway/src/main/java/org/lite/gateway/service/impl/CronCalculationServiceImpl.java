package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.CronCalculationService;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@Slf4j
public class CronCalculationServiceImpl implements CronCalculationService {
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    
    @Override
    public LocalDateTime calculateNextRun(String cronExpression) {
        return calculateNextRunAfter(cronExpression, LocalDateTime.now(UTC));
    }
    
    @Override
    public LocalDateTime calculateNextRunAfter(String cronExpression, LocalDateTime after) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            log.warn("Cannot calculate next run for null or empty cron expression");
            return null;
        }
        
        try {
            CronExpression cron = new CronExpression(cronExpression);
            cron.setTimeZone(java.util.TimeZone.getTimeZone(UTC));
            
            Date afterDate = Date.from(after.atZone(UTC).toInstant());
            Date nextFireTime = cron.getNextValidTimeAfter(afterDate);
            
            if (nextFireTime == null) {
                log.warn("No next fire time for cron expression: {}", cronExpression);
                return null;
            }
            
            return LocalDateTime.ofInstant(nextFireTime.toInstant(), UTC);
        } catch (Exception e) {
            log.error("Error calculating next run for cron expression '{}': {}", cronExpression, e.getMessage());
            return null;
        }
    }
}

