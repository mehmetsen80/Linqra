package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.lite.gateway.config.StorageProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Scheduler for monthly Knowledge Hub S3 backup bucket synchronization.
 * Uses AWS CLI `aws s3 sync --delete` to sync the primary bucket to the backup
 * bucket, removing files from backup that no longer exist in the source.
 *
 * Runs at 4:00 AM on the 1st day of every month.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubS3BackupScheduler {

    private final StorageProperties storageProperties;

    /**
     * Sync general Knowledge Hub bucket backup (Standard Sync).
     * Maintains an exact mirror of the knowledge base (documents).
     * Runs Monthly at 4:00 AM on the 1st.
     */
    @Scheduled(cron = "0 0 4 1 * ?") // Monthly at 4:00 AM on the 1st
    @SchedulerLock(name = "knowledgeHubBackup", lockAtLeastFor = "5m", lockAtMostFor = "4h")
    public void syncKnowledgeHubBucket() {
        log.info("🔄 Starting Monthly Knowledge Hub Backup Sync from {} to {}",
                storageProperties.getBucketName(), storageProperties.getBackupBucketName());

        performGeneralSync()
                .thenAccept(result -> log.info("✅ Monthly Knowledge Hub Backup Sync completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ Monthly Knowledge Hub Backup Sync failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    private CompletableFuture<String> performGeneralSync() {
        return CompletableFuture.supplyAsync(() -> {
            String sourceBucket = "s3://" + storageProperties.getBucketName();
            String backupBucket = "s3://" + storageProperties.getBackupBucketName();
            String backupRegion = storageProperties.getBackupBucketRegion();

            List<String> commandList = new ArrayList<>();
            commandList.add("aws");
            commandList.add("s3");
            commandList.add("sync");
            commandList.add(sourceBucket);
            commandList.add(backupBucket);
            commandList.add("--delete");
            commandList.add("--region");
            commandList.add(backupRegion);

            if (storageProperties.getEndpoint() != null && !storageProperties.getEndpoint().isEmpty()) {
                commandList.add("--endpoint-url");
                commandList.add(storageProperties.getEndpoint());
            }

            return executeCommand(commandList.toArray(new String[0]));
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
        log.info("🔧 Manual S3 Knowledge Hub sync triggered");
        return performGeneralSync()
                .thenApply(result -> {
                    log.info("✅ Manual S3 Knowledge Hub sync completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual S3 Knowledge Hub sync failed: {}", error.getMessage(), error);
                    return "Sync failed: " + error.getMessage();
                });
    }

    /**
     
    
     */
    public CompletableFuture<String> dryRun() {
        return CompletableFuture.supplyAsync(() -> {
            String sourceBucket = "s3://" + storageProperties.getBucketName();
            String backupBucket = "s3://" + storageProperties.getBackupBucketName();
            String backupRegion = storageProperties.getBackupBucketRegion();

            List<String> commandList = new ArrayList<>();
            commandList.add("aws");
            commandList.add("s3");
            commandList.add("sync");
            commandList.add(sourceBucket);
            commandList.add(backupBucket);
            commandList.add("--delete");
            commandList.add("--dryrun");
            commandList.add("--region");
            commandList.add(backupRegion);

            if (storageProperties.getEndpoint() != null && !storageProperties.getEndpoint().isEmpty()) {
                commandList.add("--endpoint-url");
                commandList.add(storageProperties.getEndpoint());
            }

            return executeCommand(commandList.toArray(new String[0]));
        });
    }
}
