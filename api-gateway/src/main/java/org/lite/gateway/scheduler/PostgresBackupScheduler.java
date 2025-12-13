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

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresBackupScheduler {

    @Value("${spring.postgres.username:keycloak}")
    private String postgresUser;

    @Value("${spring.postgres.password:password}")
    private String postgresPassword;

    @Value("${spring.postgres.database:keycloak}")
    private String postgresDb;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final int RETENTION_DAYS = 7;
    private static final String AWS_REGION = "us-east-1";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Hourly PostgreSQL backup.
     * Cron: "0 0 * * * ?" = At minute 0 of every hour
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void backupPostgres() {
        log.info("🔄 Starting hourly PostgreSQL backup");

        performBackup()
                .thenAccept(result -> log.info("✅ PostgreSQL backup completed: {}", result))
                .exceptionally(error -> {
                    log.error("❌ PostgreSQL backup failed: {}", error.getMessage(), error);
                    return null;
                });
    }

    /**
     * Manual trigger for testing or immediate backup.
     */
    public CompletableFuture<String> triggerBackup() {
        log.info("🔧 Manual PostgreSQL backup triggered");
        return performBackup()
                .thenApply(result -> {
                    log.info("✅ Manual PostgreSQL backup completed: {}", result);
                    return result;
                })
                .exceptionally(error -> {
                    log.error("❌ Manual PostgreSQL backup failed: {}", error.getMessage(), error);
                    return "Backup failed: " + error.getMessage();
                });
    }

    /**
     * Perform the backup using pg_dump via docker exec.
     */
    private CompletableFuture<String> performBackup() {
        return CompletableFuture.supplyAsync(() -> {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String backupFile = "postgres-backup-" + timestamp + ".sql.gz";
            String s3Bucket = getS3Bucket();
            String backupDir = getBackupDir();

            // Auto-detect container name
            String containerName = detectContainerName();

            log.info("📋 Backup configuration:");
            log.info("  Environment: {}", activeProfile);
            log.info("  Container: {}", containerName);
            log.info("  Database: {}", postgresDb);
            log.info("  S3 Bucket: {}", s3Bucket);
            log.info("  Backup File: {}", backupFile);

            try {
                // Create backup directory
                Files.createDirectories(Path.of(backupDir));

                // Execute pg_dump inside container
                // We use bash -c to handle pipes and env vars
                log.info("📦 Starting pg_dump...");

                // Construct command to run inside container
                // Export PGPASSWORD to avoid password prompt
                String dumpCommand = String.format(
                        "export PGPASSWORD='%s' && pg_dump -U %s %s | gzip > /tmp/%s",
                        postgresPassword, postgresUser, postgresDb, backupFile);

                String[] dockerCommand = {
                        "docker", "exec", containerName,
                        "bash", "-c", dumpCommand
                };

                executeCommand(dockerCommand, "pg_dump");

                // Verify backup was created inside container
                String[] verifyCommand = { "docker", "exec", containerName, "test", "-f", "/tmp/" + backupFile };
                try {
                    executeCommand(verifyCommand, "verify");
                } catch (Exception e) {
                    throw new RuntimeException("Backup file was not created inside container!");
                }

                // Get backup size
                String backupSize = getBackupSize(containerName, backupFile);
                log.info("📊 Backup size: {} bytes", backupSize);

                // Copy from container to host
                log.info("📥 Copying backup from container to host...");
                String[] copyCommand = {
                        "docker", "cp",
                        containerName + ":/tmp/" + backupFile,
                        backupDir + "/"
                };
                executeCommand(copyCommand, "docker cp");

                // Verify local copy
                File localBackup = new File(backupDir, backupFile);
                if (!localBackup.exists()) {
                    throw new RuntimeException("Failed to copy backup to host!");
                }

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

                // Clean up backup inside container
                log.info("🧹 Cleaning up backup inside container...");
                String[] cleanupCommand = { "docker", "exec", containerName, "rm", "-f", "/tmp/" + backupFile };
                try {
                    executeCommand(cleanupCommand, "cleanup");
                } catch (Exception e) {
                    log.warn("Failed to cleanup container backup: {}", e.getMessage());
                }

                // Summary
                String summary = String.format(
                        "Backup completed successfully!%n  File: %s%n  Size: %s bytes%n  Local: %s/%s%n  S3: %s/%s",
                        backupFile, backupSize, backupDir, backupFile, s3Bucket, backupFile);
                log.info("==========================================");
                log.info(summary);
                log.info("==========================================");

                return summary;

            } catch (Exception e) {
                // Cleanup on error
                try {
                    String[] cleanupCommand = { "docker", "exec", containerName, "rm", "-f", "/tmp/" + backupFile };
                    executeCommand(cleanupCommand, "error cleanup");
                } catch (Exception ignored) {
                }

                throw new RuntimeException("Backup failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Auto-detect PostgreSQL container name.
     */
    private String detectContainerName() {
        String defaultName = "postgres-service";
        try {
            // List all running containers
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "--format", "{{.Names}}");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .filter(name -> name.contains("postgres-service"))
                        .findFirst()
                        .orElse(defaultName);
            }
        } catch (Exception e) {
            log.warn("Failed to detect container name, using default: {}", defaultName);
            return defaultName;
        }
    }

    private String getS3Bucket() {
        if ("ec2".equals(activeProfile) || "prod".equals(activeProfile)) {
            return "s3://backup-linqra-postgres";
        } else {
            return "s3://backup-linqra-postgres-dev";
        }
    }

    private String getBackupDir() {
        if ("ec2".equals(activeProfile) || "prod".equals(activeProfile)) {
            return "/var/www/linqra/.kube/postgres/backups";
        } else {
            return "/Users/mehmetsen/IdeaProjects/Linqra/.kube/postgres/backups";
        }
    }

    private String getBackupSize(String containerName, String backupFile) {
        try {
            String[] command = { "docker", "exec", containerName, "stat", "-c%s", "/tmp/" + backupFile };
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void executeCommand(String[] command, String label) throws Exception {
        log.debug("Executing {}: {}", label, String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.info("{}: {}", label, line);
            }
        }

        boolean completed = process.waitFor(300, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException(label + " timed out after 300 seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException(label + " exited with code " + exitCode + ": " + output);
        }
    }

    private void cleanupOldBackups(String backupDir) {
        log.info("🧹 Cleaning up local backups older than {} days...", RETENTION_DAYS);

        try {
            File dir = new File(backupDir);
            if (!dir.exists())
                return;

            File[] backupFiles = dir
                    .listFiles((d, name) -> name.startsWith("postgres-backup-") && name.endsWith(".sql.gz"));
            if (backupFiles == null)
                return;

            long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS);
            int deletedCount = 0;

            for (File file : backupFiles) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++;
                        log.debug("Deleted old backup: {}", file.getName());
                    }
                }
            }

            log.info("🗑️ Deleted {} old backup(s)", deletedCount);

        } catch (Exception e) {
            log.warn("Failed to cleanup old backups: {}", e.getMessage());
        }
    }
}
