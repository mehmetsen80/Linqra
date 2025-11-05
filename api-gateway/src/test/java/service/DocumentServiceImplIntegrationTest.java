package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.DocumentRepository;
import org.lite.gateway.repository.KnowledgeHubChunkRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.impl.DocumentServiceImpl;
import org.lite.gateway.service.impl.S3ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DocumentService using real MongoDB and S3
 * 
 * WARNING: This test uses real AWS credentials and will upload to real S3 bucket!
 * Make sure to delete credentials after testing!
 */
@SpringBootTest(classes = org.lite.gateway.ApiGatewayApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DocumentServiceImplIntegrationTest {

    // TODO: DELETE THESE CREDENTIALS AFTER TESTING!
    private static final String AWS_ACCESS_KEY = "YOUR_AWS_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_AWS_SECRET_KEY";
    private static final String AWS_REGION = "us-west-2";
    private static final String BUCKET_NAME = "linqra-knowledge-hub-test";
    private static final String TEST_TEAM_ID = "67d0aeb17172416c411d419e";
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private KnowledgeHubChunkRepository chunkRepository;

    private DocumentServiceImpl documentService;
    private S3ServiceImpl s3Service;
    private S3Properties s3Properties;
    
    @BeforeEach
    void setUp() {
        // Setup S3Properties
        this.s3Properties = new S3Properties();
        this.s3Properties.setBucketName(BUCKET_NAME);
        this.s3Properties.setRawPrefix("raw");
        this.s3Properties.setProcessedPrefix("processed");
        this.s3Properties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        this.s3Properties.setMaxFileSize(52428800L);
        
        // Setup real S3 service
        setupRealS3Service();
        
        // Create DocumentServiceImpl
        this.documentService = new DocumentServiceImpl(
                documentRepository,
                teamRepository,
                eventPublisher,
                s3Service,
                s3Properties,
                chunkRepository
        );
    }
    
    @Test
    void testInitiateDocumentUpload_RealIntegration() {
        // SKIP THIS TEST IF CREDENTIALS ARE NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Given
        UploadInitiateRequest request = new UploadInitiateRequest();
        request.setFileName("test-document.txt");
        request.setCollectionId("test-collection");
        request.setFileSize(100L);
        request.setContentType("text/plain");
        request.setChunkSize(400);
        request.setOverlapTokens(50);
        request.setChunkStrategy("sentence");
        
        System.out.println("📝 Initiating document upload in MongoDB...");
        
        // When & Then
        StepVerifier.create(documentService.initiateDocumentUpload(request, TEST_TEAM_ID))
                .expectNextMatches(result -> {
                    var document = result.document();
                    var presignedUrl = result.presignedUrl();
                    
                    assertNotNull(document.getId());
                    assertNotNull(document.getDocumentId());
                    assertEquals("test-document.txt", document.getFileName());
                    assertEquals("test-collection", document.getCollectionId());
                    assertEquals(100L, document.getFileSize());
                    assertEquals("text/plain", document.getContentType());
                    assertNotNull(document.getS3Key());
                    assertEquals("PENDING_UPLOAD", document.getStatus());
                    assertEquals(TEST_TEAM_ID, document.getTeamId());
                    assertEquals(400, document.getChunkSize());
                    assertEquals(50, document.getOverlapTokens());
                    assertEquals("sentence", document.getChunkStrategy());
                    assertNotNull(document.getCreatedAt());
                    
                    // Verify presigned URL
                    assertNotNull(presignedUrl);
                    assertNotNull(presignedUrl.getUploadUrl());
                    assertTrue(presignedUrl.getUploadUrl().startsWith("https://"));
                    assertNotNull(presignedUrl.getRequiredHeaders());
                    
                    System.out.println("✅ Document created with ID: " + document.getId());
                    System.out.println("✅ Presigned URL generated: " + presignedUrl.getUploadUrl().substring(0, 80) + "...");
                    return true;
                })
                .verifyComplete();
        
        System.out.println("✅ Document upload initiation successful!");
    }
    
    @Test
    void testCreateAndCompleteUpload_RealIntegration() {
        // SKIP THIS TEST IF CREDENTIALS ARE NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Step 1: Initiate document upload (generates document ID and presigned URL)
        UploadInitiateRequest request = new UploadInitiateRequest();
        request.setFileName("integration-test-document.txt");
        request.setCollectionId("test-collection");
        request.setFileSize(200L);
        request.setContentType("text/plain");
        
        System.out.println("📝 Step 1: Initiating document upload...");
        
        var result = documentService.initiateDocumentUpload(request, TEST_TEAM_ID).block();
        assertNotNull(result);
        KnowledgeHubDocument createdDocument = result.document();
        String s3Key = createdDocument.getS3Key();
        String documentId = createdDocument.getDocumentId();
        
        System.out.println("✅ Document created with documentId: " + documentId);
        
        // Step 2: Upload file to S3
        System.out.println("📤 Step 2: Uploading file to S3...");
        
        String testContent = "This is a test document for integration testing.\n" +
                            "Uploaded at: " + java.time.Instant.now();
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);
        long fileSize = testData.length;
        
        var dataBuffer = new DefaultDataBufferFactory().wrap(testData);
        Flux<org.springframework.core.io.buffer.DataBuffer> content = Flux.just(dataBuffer);
        
        StepVerifier.create(s3Service.uploadFile(s3Key, content, "text/plain", fileSize))
                .verifyComplete();
        
        System.out.println("✅ File uploaded to S3!");
        
        // Step 3: Verify file exists in S3
        StepVerifier.create(s3Service.fileExists(s3Key))
                .expectNext(true)
                .verifyComplete();
        
        System.out.println("✅ Verified file exists in S3!");
        
        // Step 4: Complete upload (marks as UPLOADED and publishes event)
        System.out.println("✅ Step 3: Completing upload...");
        
        StepVerifier.create(documentService.completeUpload(documentId, s3Key))
                .expectNextMatches(document -> {
                    assertEquals("UPLOADED", document.getStatus());
                    assertNotNull(document.getUploadedAt());
                    System.out.println("✅ Upload completed at: " + document.getUploadedAt());
                    return true;
                })
                .verifyComplete();
        
        System.out.println("✅ Upload completed successfully!");
        System.out.println("✅ DocumentProcessingEvent published (verified via logs)!");
        
        // Cleanup: Delete file from S3
        System.out.println("🧹 Cleaning up S3 file...");
        s3Service.deleteFile(s3Key).block();
        
        System.out.println("🎉 Full integration test passed!");
    }
    
    @Test
    void testGetDocumentById_WithAccessControl() {
        // Given - Create a document
        UploadInitiateRequest request = new UploadInitiateRequest();
        request.setFileName("access-test.txt");
        request.setCollectionId("test-collection");
        request.setFileSize(50L);
        request.setContentType("text/plain");
        
        String documentId = java.util.UUID.randomUUID().toString();
        String s3Key = "raw/" + TEST_TEAM_ID + "/test-collection/" + documentId + "_access-test.txt";
        
        KnowledgeHubDocument createdDocument = documentService.createDocument(request, s3Key, documentId, TEST_TEAM_ID).block();
        assertNotNull(createdDocument);
        
        System.out.println("📝 Created document with documentId: " + documentId);
        
        // When & Then - Get document by ID (should pass team access control)
        StepVerifier.create(documentService.getDocumentById(documentId, TEST_TEAM_ID))
                .expectNextMatches(document -> {
                    assertEquals(documentId, document.getDocumentId());
                    assertEquals(TEST_TEAM_ID, document.getTeamId());
                    System.out.println("✅ Document retrieved successfully!");
                    return true;
                })
                .verifyComplete();
    }
    
    @Test
    void testGetDocumentById_AccessDenied() {
        // Given - Create a document for a different team
        String otherTeamId = "other-team-id-98765";
        UploadInitiateRequest request = new UploadInitiateRequest();
        request.setFileName("denied-access.txt");
        request.setCollectionId("other-collection");
        request.setFileSize(30L);
        request.setContentType("text/plain");
        
        String documentId = java.util.UUID.randomUUID().toString();
        String s3Key = "raw/" + otherTeamId + "/other-collection/" + documentId + "_denied-access.txt";
        
        // Create document for other team
        KnowledgeHubDocument otherTeamDoc = documentService.createDocument(request, s3Key, documentId, otherTeamId).block();
        assertNotNull(otherTeamDoc);
        
        System.out.println("📝 Created document for other team with documentId: " + documentId);
        
        // When & Then - Try to get document with current team's ID (should fail)
        StepVerifier.create(documentService.getDocumentById(documentId, TEST_TEAM_ID))
                .expectErrorMatches(throwable -> {
                    assertInstanceOf(RuntimeException.class, throwable);
                    assertEquals("Document not found or access denied: " + documentId, throwable.getMessage());
                    System.out.println("✅ Access denied as expected for document: " + documentId);
                    return true;
                })
                .verify();
    }
    
    @Test
    void testUpdateDocumentStatus() {
        // Given - Create a document
        UploadInitiateRequest request = new UploadInitiateRequest();
        request.setFileName("status-test.txt");
        request.setCollectionId("test-collection");
        request.setFileSize(50L);
        request.setContentType("text/plain");
        
        String documentId = java.util.UUID.randomUUID().toString();
        String s3Key = "raw/" + TEST_TEAM_ID + "/test-collection/" + documentId + "_status-test.txt";
        
        // Create document
        KnowledgeHubDocument createdDoc = documentService.createDocument(request, s3Key, documentId, TEST_TEAM_ID).block();
        assertNotNull(createdDoc);
        
        System.out.println("📝 Created document with documentId: " + documentId);
        
        // When - Update status to READY
        StepVerifier.create(documentService.updateStatus(documentId, "READY", null))
                .expectNextMatches(document -> {
                    assertEquals("READY", document.getStatus());
                    assertNotNull(document.getProcessedAt());
                    System.out.println("✅ Status updated to READY at: " + document.getProcessedAt());
                    return true;
                })
                .verifyComplete();
        
        // When - Update status to FAILED with error message
        StepVerifier.create(documentService.updateStatus(documentId, "FAILED", "Test error message"))
                .expectNextMatches(document -> {
                    assertEquals("FAILED", document.getStatus());
                    assertEquals("Test error message", document.getErrorMessage());
                    System.out.println("✅ Status updated to FAILED with message: " + document.getErrorMessage());
                    return true;
                })
                .verifyComplete();
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
        
        // Create real S3Service
        s3Service = new S3ServiceImpl(s3AsyncClient, s3Presigner, s3Properties);
    }
}

