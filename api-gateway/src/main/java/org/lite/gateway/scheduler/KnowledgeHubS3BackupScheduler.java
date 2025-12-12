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
 * Scheduler for monthly S3 backup bucket synchronization.
 * Uses AWS CLI `aws s3 sync --delete` to sync the primary bucket to the backup
 * bucket,
 * removing files from backup that no longer exist in the source.
 *
 * Runs at 3:00 AM on the 1st day of every month.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubS3BackupScheduler {

    private final KnowledgeHubS3Properties s3Properties;

    /**
     * Sync source bucket to backup bucket with delete.
     * Removes files from backup that no longer exist in source.
     * Runs at 3:00 AM on the 1st of every month.
     * Cron: second, minute, hour, day-of-month, month, weekday
     * "0 0 3 1 * ?" = At 3:00 AM on the 1st day of every month
     */
    @Scheduled(cron = "0 0 3 1 * ?") // 3:00 AM on the 1st of every month
    public void syncBackupBucket() {
        log.info("🔄 Starting monthly S3 backup sync from {} to {}",
                s3Properties.getBucketName(), s3Properties.getBackupBucketName());

        performSync()
                .thenAccept(result -> log.info("✅ Monthly S3 backup sync completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ Monthly S3 backup sync failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    /**
     * Perform the sync using AWS CLI: aws s3 sync --delete
     * This is the simplest and most reliable approach.
     */
    private CompletableFuture<String> performSync() {
        return CompletableFuture.supplyAsync(() -> {
            String sourceBucket = "s3://" + s3Properties.getBucketName();
            String backupBucket = "s3://" + s3Properties.getBackupBucketName();
            String backupRegion = s3Properties.getBackupBucketRegion();

            // Build the AWS CLI command
            // aws s3 sync s3://source s3://backup --delete --region backup-region
            String[] command = {
                    "aws", "s3", "sync",
                    sourceBucket,
                    backupBucket,
                    "--delete",
                    "--region", backupRegion
            };

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
        });
    }

    /**
     * Manual trigger for testing or immediate sync.
     * Can be called via actuator endpoint or admin API.
     */
    public CompletableFuture<String> triggerSync() {
        log.info("🔧 Manual S3 backup sync triggered");
        return performSync()
                .thenApply(result -> {
                    log.info("✅ Manual S3 backup sync completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual S3 backup sync failed: {}", error.getMessage(), error);
                    return "Sync failed: " + error.getMessage();
                });
    }

    /**
     * Dry run - see what would be synced without actually doing it.
     */
    public CompletableFuture<String> dryRun() {
        return CompletableFuture.supplyAsync(() -> {
            String sourceBucket = "s3://" + s3Properties.getBucketName();
            String backupBucket = "s3://" + s3Properties.getBackupBucketName();
            String backupRegion = s3Properties.getBackupBucketRegion();

            String[] command = {
                    "aws", "s3", "sync",
                    sourceBucket,
                    backupBucket,
                    "--delete",
                    "--dryrun",
                    "--region", backupRegion
            };

            log.info("📋 Executing dry run: {}", String.join(" ", command));

            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                process.waitFor();
                return output.toString();

            } catch (Exception e) {
                throw new RuntimeException("Failed to execute dry run: " + e.getMessage(), e);
            }
        });
    }
}
