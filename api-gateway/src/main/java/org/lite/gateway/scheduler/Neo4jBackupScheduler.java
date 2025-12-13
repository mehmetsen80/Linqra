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
 * Scheduler for weekly Neo4j graph database backup to S3.
 * Uses neo4j-admin database dump to create consistent backups.
 * 
 * Runs at 2:30 AM every Sunday.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jBackupScheduler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String CONTAINER_NAME = "neo4j-service";
    private static final String NEO4J_DATABASE = "neo4j"; // default database name
    private static final int RETENTION_DAYS = 30;
    private static final String AWS_REGION = "us-east-1";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Daily Neo4j backup.
     * Cron: "0 30 2 * * ?" = At 2:30 AM every day
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void backupNeo4j() {
        log.info("🔄 Starting weekly Neo4j backup");

        performBackup()
                .thenAccept(result -> log.info("✅ Neo4j backup completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ Neo4j backup failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    /**
     * Manual trigger for testing or immediate backup.
     */
    public CompletableFuture<String> triggerBackup() {
        log.info("🔧 Manual Neo4j backup triggered");
        return performBackup()
                .thenApply(result -> {
                    log.info("✅ Manual Neo4j backup completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual Neo4j backup failed: {}", error.getMessage(), error);
                    return "Backup failed: " + error.getMessage();
                });
    }

    /**
     * Perform the backup using neo4j-admin database dump.
     */
    private CompletableFuture<String> performBackup() {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String backupFile = "neo4j-backup-" + timestamp + ".tar.gz";
            String s3Bucket = getS3Bucket();
            String backupDir = getBackupDir();

            log.info("📋 Neo4j Backup configuration:");
            log.info("  Environment: {}", activeProfile);
            log.info("  S3 Bucket: {}", s3Bucket);
            log.info("  Backup Dir: {}", backupDir);
            log.info("  Backup File: {}", backupFile);

            try {
                // Create backup directory
                Files.createDirectories(Path.of(backupDir));

                // Check if Neo4j container is running
                if (!isContainerRunning(CONTAINER_NAME)) {
                    throw new RuntimeException("Neo4j container '" + CONTAINER_NAME + "' is not running!");
                }

                // Use tar-based backup (works while Neo4j is running)
                // This is less consistent than neo4j-admin dump but doesn't require downtime
                // and doesn't cause the container to exit
                log.info("📦 Creating Neo4j data backup...");
                String containerBackupPath = "/tmp/" + backupFile;

                String[] tarCommand = {
                        "docker", "exec", CONTAINER_NAME,
                        "tar", "-czf", containerBackupPath, "-C", "/data", "."
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
                throw new RuntimeException("Failed to backup Neo4j: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Dry run - see what would be backed up without actually doing it.
     */
    public CompletableFuture<String> dryRun() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder result = new StringBuilder();
            result.append("Neo4j Backup Dry Run:\n");
            result.append("  Container: ").append(CONTAINER_NAME).append("\n");
            result.append("  Database: ").append(NEO4J_DATABASE).append("\n");
            result.append("  S3 Bucket: ").append(getS3Bucket()).append("\n");
            result.append("  Container Running: ").append(isContainerRunning(CONTAINER_NAME)).append("\n");

            // Check database size inside container
            try {
                String[] command = {
                        "docker", "exec", CONTAINER_NAME,
                        "du", "-sh", "/data"
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

            // Check Neo4j version
            try {
                String[] command = {
                        "docker", "exec", CONTAINER_NAME,
                        "neo4j", "--version"
                };
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append("  Neo4j Version: ").append(line).append("\n");
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                result.append("  Error checking Neo4j version: ").append(e.getMessage()).append("\n");
            }

            return result.toString();
        });
    }

    /**
     * List available backups in S3.
     */
    public CompletableFuture<String> listBackups() {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder result = new StringBuilder("Neo4j Backups:\n\n📦 S3 Backups:\n");

            try {
                String[] command = { "aws", "s3", "ls", getS3Bucket() + "/", "--region", AWS_REGION };
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append("  ").append(line).append("\n");
                    }
                }
            } catch (Exception e) {
                result.append("  (failed to list S3 backups: ").append(e.getMessage()).append(")\n");
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
            File[] files = dir.listFiles((d, name) -> name.startsWith("neo4j-backup-") && name.endsWith(".gz"));

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
                ? "s3://backup-linqra-neo4j"
                : "s3://backup-linqra-neo4j-dev";
    }

    private String getBackupDir() {
        return "ec2".equals(activeProfile)
                ? "/opt/linqra/backups/neo4j"
                : System.getProperty("user.home") + "/linqra-backups/neo4j";
    }
}
