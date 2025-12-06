package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.repository.AuditLogRepository;
import org.lite.gateway.service.AuditArchivalService;
import org.lite.gateway.service.S3Service;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditArchivalServiceImpl implements AuditArchivalService {
    
    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final String AUDIT_LOG_PREFIX = "audit-logs";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    
    private final AuditLogRepository auditLogRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<Void> archiveOldLogs(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        log.info("Starting audit log archival for logs older than {} days (threshold: {})", retentionDays, threshold);
        
        return archiveLogsBefore(threshold)
                .doOnSuccess(count -> log.info("Archival completed: {} logs archived", count))
                .doOnError(error -> log.error("Archival failed: {}", error.getMessage(), error))
                .then();
    }
    
    @Override
    public Mono<Integer> archiveLogsBefore(LocalDateTime thresholdTimestamp) {
        log.info("Finding audit logs ready for archival (older than {})", thresholdTimestamp);
        
        return auditLogRepository.findLogsReadyForArchival(thresholdTimestamp)
                .collectList()
                .flatMap(logs -> {
                    if (logs.isEmpty()) {
                        log.info("No logs ready for archival");
                        return Mono.just(0);
                    }
                    
                    log.info("Found {} logs ready for archival", logs.size());
                    
                    // Group logs by date and team for efficient S3 storage
                    Map<String, List<AuditLog>> groupedLogs = logs.stream()
                            .collect(Collectors.groupingBy(log -> {
                                String dateKey = log.getTimestamp().format(DATE_FORMATTER);
                                String teamId = log.getTeamId() != null ? log.getTeamId() : "unknown";
                                return dateKey + "/" + teamId;
                            }));
                    
                    log.info("Grouped logs into {} archive files", groupedLogs.size());
                    
                    // Process each group
                    List<Mono<Integer>> archiveTasks = new ArrayList<>();
                    for (Map.Entry<String, List<AuditLog>> entry : groupedLogs.entrySet()) {
                        String groupKey = entry.getKey();
                        List<AuditLog> groupLogs = entry.getValue();
                        
                        archiveTasks.add(archiveLogGroup(groupKey, groupLogs, thresholdTimestamp));
                    }
                    
                    // Execute all archival tasks and sum the results
                    return Flux.fromIterable(archiveTasks)
                            .flatMap(task -> task)
                            .reduce(0, Integer::sum)
                            .doOnSuccess(total -> log.info("Total logs archived: {}", total));
                });
    }
    
    /**
     * Archive a group of logs (same date and team) to S3
     */
    private Mono<Integer> archiveLogGroup(String groupKey, List<AuditLog> logs, LocalDateTime thresholdTimestamp) {
        if (logs.isEmpty()) {
            return Mono.just(0);
        }
        
        // Extract date and team from group key
        String[] parts = groupKey.split("/");
        String datePath = parts[0] + "/" + parts[1] + "/" + parts[2]; // yyyy/MM/dd
        String teamId = parts.length > 3 ? parts[3] : "unknown";
        
        // Build S3 key: audit-logs/{year}/{month}/{day}/{teamId}/events-{timestamp}.json.gz
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String s3Key = String.format("%s/%s/%s/events-%s.json.gz", 
                AUDIT_LOG_PREFIX, datePath, teamId, timestamp);
        
        log.debug("Archiving {} logs to S3 key: {}", logs.size(), s3Key);
        
        try {
            // Convert logs to newline-delimited JSON (NDJSON)
            byte[] ndjsonBytes = convertToNDJSON(logs);
            
            // Compress with gzip
            byte[] compressedBytes = compressGzip(ndjsonBytes);
            
            log.debug("Compressed {} bytes to {} bytes (compression ratio: {:.2f}%)", 
                    ndjsonBytes.length, compressedBytes.length, 
                    (1.0 - (double) compressedBytes.length / ndjsonBytes.length) * 100);
            
            // Upload to S3
            return s3Service.uploadFileBytes(s3Key, compressedBytes, "application/gzip", null)
                    .then(Mono.fromCallable(() -> {
                        // Mark logs as archived in MongoDB
                        LocalDateTime archivedAt = LocalDateTime.now();
                        for (AuditLog log : logs) {
                            log.setArchivedAt(archivedAt);
                            log.setS3Key(s3Key);
                        }
                        
                        // Save all updated logs
                        return logs.size();
                    }))
                    .flatMap(count -> {
                        // Save all logs with archivedAt and s3Key set
                        return Flux.fromIterable(logs)
                                .flatMap(auditLogRepository::save)
                                .collectList()
                                .map(saved -> count);
                    })
                    .doOnSuccess(count -> log.info("Successfully archived {} logs to S3: {}", count, s3Key))
                    .doOnError(error -> log.error("Failed to archive logs to S3 key {}: {}", s3Key, error.getMessage(), error));
                    
        } catch (Exception e) {
            log.error("Error archiving log group {}: {}", groupKey, e.getMessage(), e);
            return Mono.just(0); // Return 0 on error, don't fail the entire archival process
        }
    }
    
    /**
     * Convert list of AuditLog entities to newline-delimited JSON (NDJSON)
     */
    private byte[] convertToNDJSON(List<AuditLog> logs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        for (AuditLog log : logs) {
            // Convert to JSON string
            String json = objectMapper.writeValueAsString(log);
            baos.write(json.getBytes(StandardCharsets.UTF_8));
            baos.write('\n'); // Newline delimiter
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Compress bytes with gzip
     */
    private byte[] compressGzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
        }
        return baos.toByteArray();
    }
    
    @Override
    public Mono<ArchivalStats> getArchivalStats() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(DEFAULT_RETENTION_DAYS);
        
        return Mono.zip(
                // Total logs in MongoDB
                auditLogRepository.count().defaultIfEmpty(0L),
                
                // Logs ready for archival
                auditLogRepository.countLogsReadyForArchival(threshold).defaultIfEmpty(0L),
                
                // Archived logs count (logs with archivedAt set)
                auditLogRepository.findAll()
                        .filter(log -> log.getArchivedAt() != null)
                        .count()
                        .defaultIfEmpty(0L),
                
                // Oldest log timestamp
                auditLogRepository.findAll()
                        .sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                        .next()
                        .map(AuditLog::getTimestamp)
                        .defaultIfEmpty(LocalDateTime.now()),
                
                // Newest log timestamp
                auditLogRepository.findAll()
                        .sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                        .next()
                        .map(AuditLog::getTimestamp)
                        .defaultIfEmpty(LocalDateTime.now())
        ).map(tuple -> {
            ArchivalStats stats = new ArchivalStats();
            stats.setTotalLogsInMongoDB(tuple.getT1());
            stats.setLogsReadyForArchival(tuple.getT2());
            stats.setArchivedLogsCount(tuple.getT3());
            stats.setOldestLogTimestamp(tuple.getT4());
            stats.setNewestLogTimestamp(tuple.getT5());
            return stats;
        });
    }
}

