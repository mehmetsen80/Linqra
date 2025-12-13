package org.lite.gateway.service;

import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for archiving audit logs from MongoDB (hot storage) to S3 (cold
 * storage)
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
     * Query archived audit logs from S3
     * 
     * @param teamId     Team ID to filter by (required)
     * @param startTime  Start of time range (required)
     * @param endTime    End of time range (required)
     * @param eventTypes Optional list of event types to filter by
     * @param userId     Optional user ID to filter by
     * @param result     Optional result type to filter by
     * @return Flux of matching AuditLog entries from S3 archives
     */
    Flux<AuditLog> queryArchivedLogs(
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<AuditEventType> eventTypes,
            String userId,
            String result);

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
        public long getTotalLogsInMongoDB() {
            return totalLogsInMongoDB;
        }

        public void setTotalLogsInMongoDB(long totalLogsInMongoDB) {
            this.totalLogsInMongoDB = totalLogsInMongoDB;
        }

        public long getLogsReadyForArchival() {
            return logsReadyForArchival;
        }

        public void setLogsReadyForArchival(long logsReadyForArchival) {
            this.logsReadyForArchival = logsReadyForArchival;
        }

        public long getArchivedLogsCount() {
            return archivedLogsCount;
        }

        public void setArchivedLogsCount(long archivedLogsCount) {
            this.archivedLogsCount = archivedLogsCount;
        }

        public LocalDateTime getOldestLogTimestamp() {
            return oldestLogTimestamp;
        }

        public void setOldestLogTimestamp(LocalDateTime oldestLogTimestamp) {
            this.oldestLogTimestamp = oldestLogTimestamp;
        }

        public LocalDateTime getNewestLogTimestamp() {
            return newestLogTimestamp;
        }

        public void setNewestLogTimestamp(LocalDateTime newestLogTimestamp) {
            this.newestLogTimestamp = newestLogTimestamp;
        }
    }
}
