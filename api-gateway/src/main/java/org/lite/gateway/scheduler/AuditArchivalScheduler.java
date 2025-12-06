package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.AuditArchivalService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Scheduler for automatic audit log archival
 * Runs daily at 2:00 AM to archive logs older than retention period (default: 90 days)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditArchivalScheduler {
    
    private static final int RETENTION_DAYS = 90; // Keep logs in MongoDB for 90 days
    
    private final AuditArchivalService auditArchivalService;
    
    /**
     * Archive audit logs older than retention period
     * Runs daily at 2:00 AM
     * Cron: second, minute, hour, day, month, weekday
     * "0 0 2 * * ?" = At 2:00 AM every day
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2:00 AM
    public void archiveOldAuditLogs() {
        log.info("🔄 Starting scheduled audit log archival (retention: {} days)", RETENTION_DAYS);
        
        auditArchivalService.archiveOldLogs(RETENTION_DAYS)
                .doOnSuccess(v -> log.info("✅ Scheduled audit log archival completed successfully"))
                .doOnError(error -> log.error("❌ Scheduled audit log archival failed: {}", error.getMessage(), error))
                .subscribe(
                        null, // onSuccess (void)
                        error -> log.error("Error in scheduled archival", error)
                );
    }
    
    /**
     * Optional: Manual trigger for testing or immediate archival
     * Can be called via actuator endpoint or admin API
     */
    public Mono<Void> triggerArchival() {
        log.info("🔧 Manual audit log archival triggered (retention: {} days)", RETENTION_DAYS);
        return auditArchivalService.archiveOldLogs(RETENTION_DAYS)
                .doOnSuccess(v -> log.info("✅ Manual audit log archival completed"))
                .doOnError(error -> log.error("❌ Manual audit log archival failed: {}", error.getMessage(), error));
    }
}

