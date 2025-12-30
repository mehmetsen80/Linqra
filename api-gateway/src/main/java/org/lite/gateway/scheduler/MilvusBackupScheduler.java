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
 * Scheduler for daily Milvus complete backup (Milvus + etcd + MinIO) to S3.
 * Creates a compressed tarball of all three data directories and uploads to S3.
 * 
 * Runs at 2:00 AM every day.
 */
// @Component
@RequiredArgsConstructor
@Slf4j
public class MilvusBackupScheduler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final int RETENTION_DAYS = 30;
    private static final String AWS_REGION = "us-east-1";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Daily Milvus complete backup.
     * Cron: "0 0 2 * * ?" = At 2:00 AM every day
     */
    // @Scheduled(cron = "0 0 2 * * ?")
    // @Scheduled(cron = "0 */2 * * * ?") just to test
    public void backupMilvus() {
        log.info("🔄 Starting daily Milvus complete backup");

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
     * Perform the backup by creating a tarball of all Milvus-related data
     * directories.
     * Backs up: milvus/data, etcd/data, minio/data
     */
    private CompletableFuture<String> performBackup() {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String backupFile = "milvus-complete-backup-" + timestamp + ".tar.gz";
            String s3Bucket = getS3Bucket();
            String backupDir = getBackupDir();
            String kubeDir = getKubeDir();

            log.info("📋 Milvus Complete Backup configuration:");
            log.info("  Environment: {}", activeProfile);
            log.info("  S3 Bucket: {}", s3Bucket);
            log.info("  Backup Dir: {}", backupDir);
            log.info("  Backup File: {}", backupFile);
            log.info("  Kube Dir: {}", kubeDir);

            try {
                // Create backup directory
                Files.createDirectories(Path.of(backupDir));

                // Verify data directories exist
                String[] components = { "milvus/data", "etcd/data", "minio/data" };
                for (String component : components) {
                    Path path = Path.of(kubeDir, component);
                    if (!Files.exists(path)) {
                        log.warn("⚠️ Data directory not found: {}", path);
                    } else {
                        log.info("  ✓ Found: {}", component);
                    }
                }

                // Create tarball of all three data directories
                log.info("📦 Creating Milvus complete backup...");
                File localBackup = new File(backupDir, backupFile);

                String[] tarCommand = {
                        "tar", "-czf", localBackup.getAbsolutePath(),
                        "-C", kubeDir,
                        "milvus/data", "etcd/data", "minio/data"
                };
                // Allow exit code 1: "file changed as we read it" - this is OK for live
                // database backups
                executeCommand(tarCommand, "tar", 0, 1);

                // Verify backup was created
                if (!localBackup.exists()) {
                    throw new RuntimeException("Backup file was not created!");
                }

                long fileSize = Files.size(localBackup.toPath());
                double fileSizeMB = fileSize / (1024.0 * 1024.0);
                log.info("  Backup size: {:.2f} MB", fileSizeMB);

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

                return String.format("Backup completed: %s (%.2f MB)", backupFile, fileSizeMB);

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
            result.append("Milvus Complete Backup Dry Run:\n");
            result.append("  Components: milvus, etcd, minio\n");
            result.append("  S3 Bucket: ").append(getS3Bucket()).append("\n");
            result.append("  Kube Dir: ").append(getKubeDir()).append("\n");

            String kubeDir = getKubeDir();
            String[] components = { "milvus/data", "etcd/data", "minio/data" };
            for (String component : components) {
                Path path = Path.of(kubeDir, component);
                if (Files.exists(path)) {
                    try {
                        String[] command = { "du", "-sh", path.toString() };
                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line = reader.readLine();
                            if (line != null) {
                                result.append("  ").append(component).append(": ").append(line.split("\t")[0])
                                        .append("\n");
                            }
                        }
                        process.waitFor();
                    } catch (Exception e) {
                        result.append("  ").append(component).append(": (error checking size)\n");
                    }
                } else {
                    result.append("  ").append(component).append(": NOT FOUND\n");
                }
            }

            return result.toString();
        });
    }

    private void executeCommand(String[] command, String description) throws Exception {
        executeCommand(command, description, 0); // Default: only exit code 0 is success
    }

    private void executeCommand(String[] command, String description, int... allowedExitCodes) throws Exception {
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
        boolean isAllowed = false;
        for (int allowed : allowedExitCodes) {
            if (exitCode == allowed) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new RuntimeException(description + " failed with exit code " + exitCode + ": " + output);
        }

        if (exitCode != 0) {
            log.warn("⚠️ {} completed with warning exit code {}: {}", description, exitCode, output.toString().trim());
        }
    }

    private void cleanupOldBackups(String backupDir) {
        try {
            File dir = new File(backupDir);
            File[] files = dir
                    .listFiles((d, name) -> name.startsWith("milvus-complete-backup-") && name.endsWith(".tar.gz"));

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

    private String getKubeDir() {
        return "ec2".equals(activeProfile)
                ? "/var/www/linqra/.kube"
                : System.getProperty("user.home") + "/IdeaProjects/Linqra/.kube";
    }
}
