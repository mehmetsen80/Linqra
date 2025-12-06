package org.lite.gateway.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Service for archiving audit logs from MongoDB (hot storage) to S3 (cold storage)
 */
public interface AuditArchivalService {
    
    /**
     * Archive audit logs older than the retention threshold
     * 
     * @param retentionDays Number of days to keep in MongoDB (default: 90)
     * @return Mono that completes when archival is done
     */
    Mono<Void> archiveOldLogs(int retentionDays);
    
    /**
     * Archive audit logs older than a specific timestamp
     * 
     * @param thresholdTimestamp Logs older than this timestamp will be archived
     * @return Mono with the number of logs archived
     */
    Mono<Integer> archiveLogsBefore(LocalDateTime thresholdTimestamp);
    
    /**
     * Get statistics about archival process
     * 
     * @return Mono with archival statistics
     */
    Mono<ArchivalStats> getArchivalStats();
    
    /**
     * DTO for archival statistics
     */
    class ArchivalStats {
        private long totalLogsInMongoDB;
        private long logsReadyForArchival;
        private long archivedLogsCount;
        private LocalDateTime oldestLogTimestamp;
        private LocalDateTime newestLogTimestamp;
        
        // Getters and setters
        public long getTotalLogsInMongoDB() { return totalLogsInMongoDB; }
        public void setTotalLogsInMongoDB(long totalLogsInMongoDB) { this.totalLogsInMongoDB = totalLogsInMongoDB; }
        
        public long getLogsReadyForArchival() { return logsReadyForArchival; }
        public void setLogsReadyForArchival(long logsReadyForArchival) { this.logsReadyForArchival = logsReadyForArchival; }
        
        public long getArchivedLogsCount() { return archivedLogsCount; }
        public void setArchivedLogsCount(long archivedLogsCount) { this.archivedLogsCount = archivedLogsCount; }
        
        public LocalDateTime getOldestLogTimestamp() { return oldestLogTimestamp; }
        public void setOldestLogTimestamp(LocalDateTime oldestLogTimestamp) { this.oldestLogTimestamp = oldestLogTimestamp; }
        
        public LocalDateTime getNewestLogTimestamp() { return newestLogTimestamp; }
        public void setNewestLogTimestamp(LocalDateTime newestLogTimestamp) { this.newestLogTimestamp = newestLogTimestamp; }
    }
}

