package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.KnowledgeHubS3Properties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduler for Audit Log Backup Synchronization.
 * 
 * Purpose:
 * Syncs the Primary Audit Bucket to the Backup Audit Bucket.
 * 
 * Features:
 * - Daily Schedule (3:00 AM)
 * - Additive Only (No --delete flag) to ensure immutability
 * - Targeted strictly at the dedicated audit buckets
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditBackupScheduler {

    private final KnowledgeHubS3Properties s3Properties;

    /**
     * Sync source bucket audit logs to backup bucket (Additive Only).
     * Ensures immutable history by never deleting files from backup.
     * Runs Daily at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3:00 AM
    public void syncAuditBucket() {
        log.info("🔄 Starting Daily Audit Log Integrity Sync from {} to {}",
                s3Properties.getAuditBucketName(), s3Properties.getAuditBackupBucketName());

        performAuditSync()
                .thenAccept(result -> log.info("✅ Daily Audit Log Integrity Sync completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ Daily Audit Log Integrity Sync failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    private CompletableFuture<String> performAuditSync() {
        return CompletableFuture.supplyAsync(() -> {
            String sourcePath = "s3://" + s3Properties.getAuditBucketName() + "/audit-logs";
            String backupPath = "s3://" + s3Properties.getAuditBackupBucketName() + "/audit-logs";
            String backupRegion = s3Properties.getBackupBucketRegion();

            // Compliance: No --delete flag
            String[] command = {
                    "aws", "s3", "sync",
                    sourcePath,
                    backupPath,
                    "--region", backupRegion
            };

            return executeCommand(command);
        });
    }

    private String executeCommand(String[] command) {
        log.info("📋 Executing: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.info("aws s3 sync: {}", line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "Sync completed successfully.\n" + output;
            } else {
                throw new RuntimeException("AWS CLI exited with code " + exitCode + ": " + output);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to execute aws s3 sync: " + e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for testing or immediate sync.
     */
    public CompletableFuture<String> triggerSync() {
        log.info("🔧 Manual S3 Audit sync triggered");
        return performAuditSync()
                .thenApply(result -> {
                    log.info("✅ Manual S3 Audit sync completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual S3 Audit sync failed: {}", error.getMessage(), error);
                    return "Sync failed: " + error.getMessage();
                });
    }

    /**
     * Dry run - Audit Sync dry run.
     */
    public CompletableFuture<String> dryRun() {
        return CompletableFuture.supplyAsync(() -> {
            String sourcePath = "s3://" + s3Properties.getAuditBucketName() + "/audit-logs";
            String backupPath = "s3://" + s3Properties.getAuditBackupBucketName() + "/audit-logs";
            String backupRegion = s3Properties.getBackupBucketRegion();

            String[] command = {
                    "aws", "s3", "sync",
                    sourcePath,
                    backupPath,
                    "--dryrun",
                    "--region", backupRegion
            };

            return executeCommand(command);
        });
    }
}
