package service;

import org.junit.jupiter.api.Test;
import org.lite.gateway.config.KnowledgeHubS3Properties;
import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.service.impl.S3ServiceImpl;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for S3Service using real AWS S3
 * 
 * WARNING: This test uses real AWS credentials and will upload to real S3
 * bucket!
 * Make sure to delete credentials after testing!
 */
// @SpringBootTest
class S3ServiceImplIntegrationTest {

    // TODO: DELETE THESE CREDENTIALS AFTER TESTING!
    private static final String AWS_ACCESS_KEY = "YOUR_AWS_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_AWS_SECRET_KEY";
    private static final String AWS_REGION = "us-west-2";
    private static final String BUCKET_NAME = "linqra-knowledge-hub-dev";

    private S3ServiceImpl s3Service;
    private KnowledgeHubS3Properties s3Properties;

    @Test
    void testUploadFileToS3_RealIntegration() {
        // SKIP THIS TEST IF CREDENTIALS ARE NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }

        // Given
        setupRealS3Service();

        String s3Key = "raw/test-team/test-collection/" + System.currentTimeMillis() + "_test-file.txt";
        String contentType = "text/plain";
        long fileSize = 0L; // Will be set from actual data

        // Create test content
        String testContent = "This is a test file uploaded from S3ServiceImplIntegrationTest at " +
                System.currentTimeMillis();
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);
        fileSize = testData.length;

        var dataBuffer = new DefaultDataBufferFactory().wrap(testData);
        Flux<org.springframework.core.io.buffer.DataBuffer> content = Flux.just(dataBuffer);

        System.out.println("📤 Uploading to S3: " + BUCKET_NAME + "/" + s3Key);

        // When & Then
        StepVerifier.create(s3Service.uploadFile(s3Key, content, contentType, fileSize))
                .verifyComplete();

        System.out.println("✅ Successfully uploaded file to S3!");

        // Verify file exists
        StepVerifier.create(s3Service.fileExists(s3Key))
                .expectNext(true)
                .verifyComplete();

        System.out.println("✅ Verified file exists in S3!");

        // Test generate presigned download URL
        StepVerifier.create(s3Service.generatePresignedDownloadUrl(s3Key))
                .expectNextMatches(url -> url != null && url.startsWith("https://"))
                .verifyComplete();

        System.out.println("✅ Generated presigned download URL!");

        // Clean up - Delete the test file
        StepVerifier.create(s3Service.deleteFile(s3Key))
                .verifyComplete();

        System.out.println("🧹 Cleaned up test file from S3");

        // Verify file is deleted
        StepVerifier.create(s3Service.fileExists(s3Key))
                .expectNext(false)
                .verifyComplete();

        System.out.println("✅ Verified file is deleted from S3!");

        System.out.println("🎉 All integration tests passed!");
    }

    @Test
    void testUploadFile_KeepInS3() {
        // Upload a file and KEEP IT in S3 for manual verification in AWS Console
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping upload test - AWS credentials not set");
            return;
        }

        setupRealS3Service();

        String s3Key = "raw/test-team/test-collection/VERIFY_UPLOAD_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";

        String testContent = "This is a test file for manual verification.\n" +
                "Uploaded at: " + java.time.Instant.now() + "\n" +
                "If you can see this in AWS S3 Console, upload is working!";
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);
        long fileSize = testData.length;

        var dataBuffer = new DefaultDataBufferFactory().wrap(testData);
        Flux<org.springframework.core.io.buffer.DataBuffer> content = Flux.just(dataBuffer);

        System.out.println("📤 Uploading to S3: " + BUCKET_NAME + "/" + s3Key);
        System.out.println("🔍 Verify in AWS Console at: s3://" + BUCKET_NAME + "/" + s3Key);

        StepVerifier.create(s3Service.uploadFile(s3Key, content, contentType, fileSize))
                .verifyComplete();

        System.out.println("✅ File uploaded! Check AWS S3 Console to verify.");
        System.out.println("🗑️  Run testDeleteFile to clean up this file");
    }

    @Test
    void testDeleteFile_FromS3() {
        // Delete a specific file from S3
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping delete test - AWS credentials not set");
            return;
        }

        setupRealS3Service();

        // TODO: Set this to the S3 key you want to delete
        String s3Key = "raw/test-team/test-collection/VERIFY_UPLOAD_1761864802696.txt";

        System.out.println("🗑️  Deleting from S3: " + BUCKET_NAME + "/" + s3Key);

        // Check if file exists
        boolean exists = Boolean.TRUE.equals(s3Service.fileExists(s3Key).block());

        if (exists) {
            System.out.println("✅ File exists in S3");

            // Delete the file
            s3Service.deleteFile(s3Key).block();
            System.out.println("✅ File deleted from S3!");

            // Verify deletion
            StepVerifier.create(s3Service.fileExists(s3Key))
                    .expectNext(false)
                    .verifyComplete();

            System.out.println("✅ Verified file is deleted!");
        } else {
            System.out.println("⚠️  File does not exist in S3 - nothing to delete");
        }
    }

    @Test
    void testGeneratePresignedUploadUrl_RealIntegration() {
        // SKIP THIS TEST IF CREDENTIALS ARE NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping presigned URL test - AWS credentials not set");
            return;
        }

        // Given
        setupRealS3Service();

        String s3Key = "raw/test-team/test-collection/" + System.currentTimeMillis() + "_presigned-test.txt";
        String contentType = "text/plain";

        System.out.println("🔑 Generating presigned upload URL for: " + s3Key);

        // When
        var result = s3Service.generatePresignedUploadUrl(s3Key, contentType);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(url -> {
                    assertNotNull(url.getUploadUrl());
                    assertNotNull(url.getS3Key());
                    assertNotNull(url.getExpiresAt());
                    assertNotNull(url.getRequiredHeaders());
                    assertTrue(url.getUploadUrl().startsWith("https://"));
                    assertTrue(url.getRequiredHeaders().containsKey("Content-Type"));

                    System.out.println("✅ Generated presigned URL: " + url.getUploadUrl());
                    System.out.println("📅 Expires at: " + url.getExpiresAt());
                    System.out.println("⏰ Expires in: " + url.getExpiresInSeconds() + " seconds");
                    System.out.println("");
                    System.out.println("📝 To test this URL, run this cURL command:");
                    System.out.println("curl -X PUT '" + url.getUploadUrl() + "' \\");
                    System.out.println("  -H 'Content-Type: " + contentType + "' \\");
                    System.out.println("  --data-binary 'Test content uploaded via presigned URL'");
                    System.out.println("");
                    System.out.println("⚠️  Note: Clicking the URL in browser won't work - it expects PUT request!");

                    return true;
                })
                .verifyComplete();
    }

    @Test
    void testUploadFileWithPresignedUrl_RealIntegration() {
        // Test uploading a file using a presigned URL
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping presigned upload test - AWS credentials not set");
            return;
        }

        setupRealS3Service();

        String s3Key = "raw/test-team/test-collection/PRESIGNED_UPLOAD_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";

        System.out.println("🔑 Step 1: Generating presigned upload URL for: " + s3Key);

        // Generate presigned URL
        PresignedUploadUrl presignedUrl = s3Service.generatePresignedUploadUrl(s3Key, contentType).block();

        assertNotNull(presignedUrl);
        System.out.println("✅ Generated presigned URL");
        System.out.println("📤 Step 2: Uploading file using presigned URL...");

        // Create test content
        String testContent = "This file was uploaded using a presigned URL!";
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);

        // Upload using presigned URL
        try {
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(presignedUrl.getUploadUrl()))
                    .method("PUT", java.net.http.HttpRequest.BodyPublishers.ofByteArray(testData))
                    .header("Content-Type", contentType)
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            System.out.println("Response status: " + response.statusCode());

            if (response.statusCode() == 200) {
                System.out.println("✅ Successfully uploaded file using presigned URL!");

                // Verify file exists
                StepVerifier.create(s3Service.fileExists(s3Key))
                        .expectNext(true)
                        .verifyComplete();

                System.out.println("✅ Verified file exists in S3!");

                // Clean up
                s3Service.deleteFile(s3Key).block();
                System.out.println("🧹 Cleaned up test file");
            } else {
                System.out.println("❌ Upload failed with status: " + response.statusCode());
                System.out.println("Response body: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("❌ Error uploading via presigned URL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testFileExists_RealIntegration() {
        // Test checking if a file exists in S3
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) ||
                "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping file exists test - AWS credentials not set");
            return;
        }

        setupRealS3Service();

        // Test with existing file
        // String existingKey =
        // "raw/test-team/test-collection/VERIFY_UPLOAD_1761864802696.txt";
        // String nonExistentKey = "raw/test-team/test-collection/NON_EXISTENT_FILE_" +
        // System.currentTimeMillis() + ".txt";

        String existingKey = "raw/67d0aeb17172416c411d419e/690693fdcd90d04617697736/c20a402f-b953-4c74-80e8-fc0603cbe301_i-485.pdf";
        String nonExistentKey = "raw/test-team/test-collection/NON_EXISTENT_FILE_" + System.currentTimeMillis()
                + ".txt";

        System.out.println("🔍 Testing file existence - existing file");

        // Check existing file (if any)
        boolean exists = Boolean.TRUE.equals(s3Service.fileExists(existingKey).block());
        System.out.println("✅ File exists check result: " + exists);

        // Check non-existent file
        System.out.println("🔍 Testing file existence - non-existent file");
        StepVerifier.create(s3Service.fileExists(nonExistentKey))
                .expectNext(false)
                .verifyComplete();

        System.out.println("✅ Verified non-existent file returns false!");
    }

    private void setupRealS3Service() {
        // Create real AWS credentials
        var credentials = AwsBasicCredentials.create(AWS_ACCESS_KEY, AWS_SECRET_KEY);
        var credentialsProvider = StaticCredentialsProvider.create(credentials);

        // Create real S3 clients
        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(credentialsProvider)
                .build();

        S3Presigner s3Presigner = S3Presigner.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(credentialsProvider)
                .build();

        // Create real KnowledgeHubS3Properties
        s3Properties = new KnowledgeHubS3Properties();
        s3Properties.setBucketName(BUCKET_NAME);
        s3Properties.setRawPrefix("raw");
        s3Properties.setProcessedPrefix("processed");
        s3Properties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        s3Properties.setMaxFileSize(52428800L);

        // Create real S3Service
        s3Service = new S3ServiceImpl(s3AsyncClient, s3Presigner, s3Properties);
    }
}
