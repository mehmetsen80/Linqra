package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.KnowledgeHubS3Properties;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.repository.AuditLogRepository;
import org.lite.gateway.service.AuditArchivalService;
import org.lite.gateway.service.S3Service;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.lite.gateway.service.ChunkEncryptionService;

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
    private final ChunkEncryptionService chunkEncryptionService;
    private final KnowledgeHubS3Properties s3Properties;

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
                            .reduce(0, (a, b) -> a + b)
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

        return chunkEncryptionService.getCurrentKeyVersion(teamId)
                .flatMap(keyVersion -> {
                    // Build S3 key with version:
                    // audit-logs/{year}/{month}/{day}/{teamId}/events-{timestamp}-{version}.json.gz
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    String s3Key = String.format("%s/%s/%s/events-%s-%s.json.gz",
                            AUDIT_LOG_PREFIX, datePath, teamId, timestamp, keyVersion);

                    log.debug("Archiving {} logs to S3 key: {}", logs.size(), s3Key);

                    try {
                        // Convert logs to newline-delimited JSON (NDJSON)
                        byte[] ndjsonBytes = convertToNDJSON(logs);

                        // Compress with gzip
                        byte[] compressedBytes = compressGzip(ndjsonBytes);

                        log.debug("Compressed {} bytes to {} bytes (compression ratio: {:.2f}%)",
                                ndjsonBytes.length, compressedBytes.length,
                                (1.0 - (double) compressedBytes.length / ndjsonBytes.length) * 100);

                        // Encrypt
                        return chunkEncryptionService.encryptFile(compressedBytes, teamId, keyVersion)
                                .flatMap(encryptedBytes -> {
                                    // Upload to S3 Dedicated Audit Bucket
                                    String auditBucket = s3Properties.getAuditBucketName();
                                    return s3Service
                                            .uploadFileBytes(auditBucket, s3Key, encryptedBytes, "application/gzip",
                                                    keyVersion)
                                            .then(Mono.defer(() -> {
                                                // Delete logs from MongoDB after successful archival
                                                log.debug("Deleting {} archived logs from MongoDB", logs.size());
                                                return auditLogRepository.deleteAll(logs)
                                                        .thenReturn(logs.size());
                                            }));
                                });

                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .doOnSuccess(
                        count -> log.info("Successfully archived and deleted {} logs from MongoDB (Encrypted)", count))
                .doOnError(error -> log.error("Failed to archive logs to S3: {}", error.getMessage(), error))
                .onErrorResume(e -> {
                    log.error("Error archiving log group {}: {}", groupKey, e.getMessage());
                    return Mono.just(0);
                });
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
    public Flux<AuditLog> queryArchivedLogs(
            String teamId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<AuditEventType> eventTypes,
            String userId,
            String result) {
        log.info("Querying archived logs for team {} from {} to {}", teamId, startTime, endTime);

        // Generate list of date prefixes to search
        List<String> prefixes = generateDatePrefixes(teamId, startTime, endTime);
        log.debug("Generated {} S3 prefixes to search", prefixes.size());

        // Query each prefix and combine results
        return Flux.fromIterable(prefixes)
                .flatMap(prefix -> s3Service.listFiles(prefix)
                        .flatMapMany(s3Objects -> {
                            log.debug("Found {} files under prefix {}", s3Objects.size(), prefix);
                            return Flux.fromIterable(s3Objects)
                                    .filter(obj -> obj.key().endsWith(".json.gz"))
                                    .flatMap(obj -> downloadAndParseArchive(obj.key(), teamId));
                        })
                        .onErrorResume(e -> {
                            log.warn("Error querying prefix {}: {}", prefix, e.getMessage());
                            return Flux.empty();
                        }))
                // Filter by team (should match, but verify)
                .filter(auditLog -> teamId.equals(auditLog.getTeamId()))
                // Filter by timestamp range
                .filter(auditLog -> {
                    LocalDateTime ts = auditLog.getTimestamp();
                    return ts != null && !ts.isBefore(startTime) && !ts.isAfter(endTime);
                })
                // Filter by event types if specified
                .filter(auditLog -> {
                    if (eventTypes == null || eventTypes.isEmpty()) {
                        return true;
                    }
                    return eventTypes.contains(auditLog.getEventType());
                })
                // Filter by user if specified
                .filter(auditLog -> {
                    if (userId == null || userId.isEmpty()) {
                        return true;
                    }
                    return userId.equals(auditLog.getUserId());
                })
                // Filter by result if specified
                .filter(auditLog -> {
                    if (result == null || result.isEmpty()) {
                        return true;
                    }
                    return result.equalsIgnoreCase(auditLog.getResult());
                })
                // Sort by timestamp descending (newest first)
                .sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .doOnComplete(() -> log.info("Completed querying archived logs for team {}", teamId));
    }

    /**
     * Generate S3 prefixes for each date in the range
     */
    private List<String> generateDatePrefixes(String teamId, LocalDateTime startTime, LocalDateTime endTime) {
        List<String> prefixes = new ArrayList<>();
        LocalDate currentDate = startTime.toLocalDate();
        LocalDate end = endTime.toLocalDate();

        while (!currentDate.isAfter(end)) {
            String prefix = String.format("%s/%s/%s",
                    AUDIT_LOG_PREFIX,
                    currentDate.format(DATE_FORMATTER),
                    teamId);
            prefixes.add(prefix);
            currentDate = currentDate.plusDays(1);
        }

        return prefixes;
    }

    /**
     * Download and parse a gzipped NDJSON archive file from S3
     */
    private Flux<AuditLog> downloadAndParseArchive(String s3Key, String teamId) {
        log.debug("Downloading and parsing archive: {}", s3Key);

        return s3Service.downloadFileContent(s3Key)
                .flatMapMany(fileBytes -> {
                    Mono<byte[]> decryptedBytesMono;

                    // Extact version from filename: events-{timestamp}-{version}.json.gz
                    // Legacy: events-{timestamp}.json.gz
                    String version = extractVersionFromKey(s3Key);

                    if (version != null) {
                        // Decrypt using specified version
                        decryptedBytesMono = chunkEncryptionService.decryptFile(fileBytes, teamId, version);
                    } else {
                        // Assume unencrypted legacy file
                        decryptedBytesMono = Mono.just(fileBytes);
                    }

                    return decryptedBytesMono
                            .flatMapMany(decryptedBytes -> {
                                try {
                                    // Decompress gzip
                                    byte[] decompressedBytes = decompressGzip(decryptedBytes);

                                    // Parse NDJSON (each line is a JSON object)
                                    List<AuditLog> logs = new ArrayList<>();
                                    try (BufferedReader reader = new BufferedReader(
                                            new InputStreamReader(new ByteArrayInputStream(decompressedBytes),
                                                    StandardCharsets.UTF_8))) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            if (!line.trim().isEmpty()) {
                                                try {
                                                    AuditLog auditLog = objectMapper.readValue(line, AuditLog.class);
                                                    logs.add(auditLog);
                                                } catch (Exception e) {
                                                    log.warn("Failed to parse audit log line: {}", e.getMessage());
                                                }
                                            }
                                        }
                                    }

                                    log.debug("Parsed {} audit logs from {}", logs.size(), s3Key);
                                    return Flux.fromIterable(logs);

                                } catch (IOException e) {
                                    log.error("Error parsing archive {}: {}", s3Key, e.getMessage(), e);
                                    return Flux.empty();
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Error processing archive {}: {}", s3Key, e.getMessage());
                    return Flux.empty();
                });
    }

    private String extractVersionFromKey(String s3Key) {
        // Filename format: events-{timestamp}-{version}.json.gz
        // Regex to extract version
        try {
            if (s3Key.contains("-v")) {
                int vIndex = s3Key.lastIndexOf("-v");
                int dotIndex = s3Key.indexOf(".json.gz");
                if (vIndex > 0 && dotIndex > vIndex) {
                    return s3Key.substring(vIndex + 1, dotIndex); // extract "v1", "v2" etc.
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract version from key: {}", s3Key);
        }
        return null;
    }

    /**
     * Decompress gzip bytes
     */
    private byte[] decompressGzip(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
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
                        .defaultIfEmpty(LocalDateTime.now()))
                .map(tuple -> {
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
