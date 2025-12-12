package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for weekly Milvus vector database backup to S3.
 * Creates a compressed tarball of the Milvus data directory and uploads to S3.
 * 
 * Runs at 2:00 AM every Sunday.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MilvusBackupScheduler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String CONTAINER_NAME = "milvus-service";
    private static final String MILVUS_DATA_PATH = "/var/lib/milvus";
    private static final int RETENTION_DAYS = 30;
    private static final String AWS_REGION = "us-east-1";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Daily Milvus backup.
     * Cron: "0 0 2 * * ?" = At 2:00 AM every day
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void backupMilvus() {
        log.info("🔄 Starting weekly Milvus backup");

        performBackup()
                .thenAccept(result -> log.info("✅ Milvus backup completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ Milvus backup failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    /**
     * Manual trigger for testing or immediate backup.
     */
    public CompletableFuture<String> triggerBackup() {
        log.info("🔧 Manual Milvus backup triggered");
        return performBackup()
                .thenApply(result -> {
                    log.info("✅ Manual Milvus backup completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual Milvus backup failed: {}", error.getMessage(), error);
                    return "Backup failed: " + error.getMessage();
                });
    }

    /**
     * Perform the backup by creating a tarball of Milvus data and uploading to S3.
     */
    private CompletableFuture<String> performBackup() {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String backupFile = "milvus-backup-" + timestamp + ".tar.gz";
            String s3Bucket = getS3Bucket();
            String backupDir = getBackupDir();

            log.info("📋 Milvus Backup configuration:");
            log.info("  Environment: {}", activeProfile);
            log.info("  S3 Bucket: {}", s3Bucket);
            log.info("  Backup Dir: {}", backupDir);
            log.info("  Backup File: {}", backupFile);

            try {
                // Create backup directory
                Files.createDirectories(Path.of(backupDir));

                // Check if Milvus container is running
                if (!isContainerRunning(CONTAINER_NAME)) {
                    throw new RuntimeException("Milvus container '" + CONTAINER_NAME + "' is not running!");
                }

                // Create tarball of Milvus data directory inside container
                log.info("📦 Creating Milvus data backup...");
                String containerBackupPath = "/tmp/" + backupFile;

                String[] tarCommand = {
                        "docker", "exec", CONTAINER_NAME,
                        "tar", "-czf", containerBackupPath, "-C", MILVUS_DATA_PATH, "."
                };
                executeCommand(tarCommand, "docker exec tar");

                // Copy backup from container to host
                log.info("📤 Copying backup from container...");
                File localBackup = new File(backupDir, backupFile);
                String[] copyCommand = {
                        "docker", "cp",
                        CONTAINER_NAME + ":" + containerBackupPath,
                        localBackup.getAbsolutePath()
                };
                executeCommand(copyCommand, "docker cp");

                // Clean up container backup file
                String[] cleanupCommand = {
                        "docker", "exec", CONTAINER_NAME,
                        "rm", "-f", containerBackupPath
                };
                executeCommand(cleanupCommand, "docker exec rm");

                // Upload to S3
                log.info("☁️ Uploading backup to S3...");
                String[] s3Command = {
                        "aws", "s3", "cp",
                        localBackup.getAbsolutePath(),
                        s3Bucket + "/" + backupFile,
                        "--region", AWS_REGION
                };

                try {
                    executeCommand(s3Command, "aws s3 cp");
                    log.info("✅ Successfully uploaded to S3");
                } catch (Exception e) {
                    log.error("⚠️ Failed to upload to S3: {} - keeping local backup", e.getMessage());
                }

                // Clean up old local backups
                cleanupOldBackups(backupDir);

                long fileSize = Files.size(localBackup.toPath());
                return String.format("Backup completed: %s (%.2f MB)", backupFile, fileSize / (1024.0 * 1024.0));

            } catch (Exception e) {
                throw new RuntimeException("Failed to backup Milvus: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Dry run - see what would be backed up without actually doing it.
     */
    public CompletableFuture<String> dryRun() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder result = new StringBuilder();
            result.append("Milvus Backup Dry Run:\n");
            result.append("  Container: ").append(CONTAINER_NAME).append("\n");
            result.append("  Data Path: ").append(MILVUS_DATA_PATH).append("\n");
            result.append("  S3 Bucket: ").append(getS3Bucket()).append("\n");
            result.append("  Container Running: ").append(isContainerRunning(CONTAINER_NAME)).append("\n");

            // Check data directory size inside container
            try {
                String[] command = {
                        "docker", "exec", CONTAINER_NAME,
                        "du", "-sh", MILVUS_DATA_PATH
                };
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append("  Data Size: ").append(line).append("\n");
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                result.append("  Error checking data size: ").append(e.getMessage()).append("\n");
            }

            return result.toString();
        });
    }

    private boolean isContainerRunning(String containerName) {
        try {
            String[] command = { "docker", "inspect", "-f", "{{.State.Running}}", containerName };
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.readLine();
                return "true".equals(output);
            }
        } catch (Exception e) {
            log.warn("Failed to check container status: {}", e.getMessage());
            return false;
        }
    }

    private void executeCommand(String[] command, String description) throws Exception {
        log.debug("Executing: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("{}: {}", description, line);
            }
        }

        boolean completed = process.waitFor(30, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException(description + " timed out after 30 minutes");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException(description + " failed with exit code " + exitCode + ": " + output);
        }
    }

    private void cleanupOldBackups(String backupDir) {
        try {
            File dir = new File(backupDir);
            File[] files = dir.listFiles((d, name) -> name.startsWith("milvus-backup-") && name.endsWith(".tar.gz"));

            if (files != null) {
                long cutoffTime = System.currentTimeMillis() - (RETENTION_DAYS * 24L * 60L * 60L * 1000L);
                for (File file : files) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            log.info("🗑️ Deleted old backup: {}", file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old backups: {}", e.getMessage());
        }
    }

    private String getS3Bucket() {
        return "ec2".equals(activeProfile)
                ? "s3://backup-linqra-milvus"
                : "s3://backup-linqra-milvus-dev";
    }

    private String getBackupDir() {
        return "ec2".equals(activeProfile)
                ? "/opt/linqra/backups/milvus"
                : System.getProperty("user.home") + "/linqra-backups/milvus";
    }
}
