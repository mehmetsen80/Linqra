package service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.entity.KnowledgeHubChunk;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.KnowledgeHubChunkRepository;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.ChunkingService;
import org.lite.gateway.service.TikaDocumentParser;
import org.lite.gateway.service.impl.KnowledgeHubDocumentProcessingServiceImpl;
import org.lite.gateway.service.impl.S3ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.AbstractMessageChannel;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KnowledgeHubDocumentProcessingService using real MongoDB and S3
 * 
 * WARNING: This test uses real AWS credentials and will upload to real S3 bucket!
 * Make sure to delete credentials after testing!
 */
@SpringBootTest(classes = org.lite.gateway.ApiGatewayApplication.class, 
                webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class DocumentProcessingServiceImplIntegrationTest {

    // TODO: DELETE THESE CREDENTIALS AFTER TESTING!
    private static final String AWS_ACCESS_KEY = "YOUR_AWS_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_AWS_SECRET_KEY";
    private static final String AWS_REGION = "us-west-2";
    private static final String BUCKET_NAME = "linqra-knowledge-hub-test";
    private static final String TEST_TEAM_ID = "67d0aeb17172416c411d419e";
    private static final String TEST_COLLECTION_ID = "test-collection-processing";
    
    // Existing USCIS form document ID (file already uploaded to S3)
    // TODO: Replace with your actual existing documentId
    private static final String EXISTING_USCIS_DOCUMENT_ID = "YOUR_EXISTING_DOCUMENT_ID_HERE";
    
    // Set to false to keep processed S3 files for inspection
    private static final boolean CLEANUP_AFTER_TEST = false;
    
    @Autowired
    private KnowledgeHubDocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeHubChunkRepository chunkRepository;
    
    @Autowired(required = false)
    private ChunkEncryptionService chunkEncryptionService;
    
    private KnowledgeHubDocumentProcessingServiceImpl documentProcessingService;
    private S3ServiceImpl s3Service;
    private S3Properties s3Properties;

    @BeforeEach
    void setUp() {
        // Skip setup if credentials are not configured
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            return;
        }
        
        // Setup S3Properties
        this.s3Properties = new S3Properties();
        this.s3Properties.setBucketName(BUCKET_NAME);
        this.s3Properties.setRawPrefix("raw");
        this.s3Properties.setProcessedPrefix("processed");
        this.s3Properties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        this.s3Properties.setMaxFileSize(52428800L);
        
        // Setup real S3 service
        setupRealS3Service();
        
        // Setup Tika and Chunking services
        TikaDocumentParser tikaDocumentParser = new TikaDocumentParser();
        ChunkingService chunkingService = new ChunkingService();
        
        // Create a no-op MessageChannel for testing (WebSocket publishing not needed in integration tests)
        MessageChannel mockExecutionMessageChannel = new AbstractMessageChannel() {
            @Override
            protected boolean sendInternal(@NonNull Message<?> message, long timeout) {
                // No-op: just return true to simulate successful message sending
                return true;
            }
        };
        
        // Create a no-op ApplicationEventPublisher for testing (events not needed in integration tests)
        org.springframework.context.ApplicationEventPublisher mockEventPublisher = event -> {
            // No-op: just log that event would be published
            // log.debug("Event published: {}", event.getClass().getSimpleName());
        };
        
        // Create a no-op ChunkEncryptionService for testing if not autowired
        // This passes through text without encryption for integration tests
        ChunkEncryptionService encryptionService = chunkEncryptionService != null 
            ? chunkEncryptionService 
            : createNoOpEncryptionService();
        
        // Create KnowledgeHubDocumentProcessingServiceImpl
        this.documentProcessingService = new KnowledgeHubDocumentProcessingServiceImpl(
                documentRepository,
                chunkRepository,
                s3Service,
                tikaDocumentParser,
                chunkingService,
                s3Properties,
                mockEventPublisher,
                encryptionService,
                mockExecutionMessageChannel
        );
    }
    
    /**
     * Create a no-op ChunkEncryptionService for testing that passes through text without encryption.
     * This allows tests to run without requiring vault setup.
     */
    private ChunkEncryptionService createNoOpEncryptionService() {
        return new ChunkEncryptionService() {
            @Override
            public String encryptChunkText(String plaintext, String teamId) {
                return plaintext; // No encryption for tests
            }
            
            @Override
            public String encryptChunkText(String plaintext, String teamId, String keyVersion) {
                return plaintext; // No encryption for tests
            }
            
            @Override
            public String decryptChunkText(String encryptedText, String teamId, String keyVersion) {
                return encryptedText; // No decryption for tests (assumes already plaintext)
            }
            
            @Override
            public String getCurrentKeyVersion() {
                return "v1"; // Default version
            }
            
            @Override
            public byte[] encryptFile(byte[] fileBytes, String teamId) {
                return fileBytes; // No encryption for tests
            }
            
            @Override
            public byte[] encryptFile(byte[] fileBytes, String teamId, String keyVersion) {
                return fileBytes; // No encryption for tests
            }
            
            @Override
            public byte[] decryptFile(byte[] encryptedBytes, String teamId, String keyVersion) {
                return encryptedBytes; // No decryption for tests (assumes already plaintext)
            }
        };
    }
    
    @Test
    void testProcessDocument_CompleteFlow() {
        // SKIP THIS TEST IF CREDENTIALS ARE NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Given - Create a test document with UPLOADED status
        String documentId = UUID.randomUUID().toString();
        String fileName = "integration-test-processing.txt";
        String s3Key = "raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/" + documentId + "_" + fileName;
        
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(documentId)
                .fileName(fileName)
                .collectionId(TEST_COLLECTION_ID)
                .fileSize(0L) // Will be set after upload
                .contentType("text/plain")
                .s3Key(s3Key)
                .status("UPLOADED")
                .teamId(TEST_TEAM_ID)
                .chunkSize(400)
                .overlapTokens(50)
                .chunkStrategy("sentence")
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        System.out.println("📝 Step 1: Creating document in MongoDB...");
        KnowledgeHubDocument savedDocument = documentRepository.save(document).block();
        assertNotNull(savedDocument);
        System.out.println("✅ Document created with documentId: " + documentId);
        
        // Upload test file content to S3
        System.out.println("📤 Step 2: Uploading test file to S3...");
        String testContent = "This is a test document for document processing integration testing.\n" +
                            "It contains multiple sentences. Each sentence should be properly detected by the NLP model.\n" +
                            "The document processing service should parse this text, chunk it, and save the chunks to MongoDB.\n" +
                            "It should also create a processed JSON document and save it back to S3.\n" +
                            "Finally, it should update the document status to PROCESSED.\n" +
                            "This is the last sentence of the test document.";
        
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);
        savedDocument.setFileSize((long) testData.length);
        documentRepository.save(savedDocument).block();
        
        var dataBuffer = new DefaultDataBufferFactory().wrap(testData);
        Flux<org.springframework.core.io.buffer.DataBuffer> content = Flux.just(dataBuffer);
        
        StepVerifier.create(s3Service.uploadFile(s3Key, content, "text/plain", testData.length))
                .verifyComplete();
        
        System.out.println("✅ File uploaded to S3: " + s3Key);
        
        // Verify file exists before processing
        StepVerifier.create(s3Service.fileExists(s3Key))
                .expectNext(true)
                .verifyComplete();
        
        // Verify initial status is UPLOADED
        KnowledgeHubDocument beforeProcessing = documentRepository.findByDocumentId(documentId).block();
        assertNotNull(beforeProcessing);
        assertEquals("UPLOADED", beforeProcessing.getStatus());
        System.out.println("✅ Verified initial status is UPLOADED");
        
        // When - Process the document
        System.out.println("⚙️  Step 3: Processing document...");
        
        StepVerifier.create(documentProcessingService.processDocument(documentId, TEST_TEAM_ID))
                .verifyComplete();
        
        System.out.println("✅ Document processing completed!");
        
        // Then - Verify document status changed to PROCESSED
        System.out.println("🔍 Step 4: Verifying document status...");
        KnowledgeHubDocument afterProcessing = documentRepository.findByDocumentId(documentId).block();
        assertNotNull(afterProcessing);
        assertEquals("PROCESSED", afterProcessing.getStatus());
        assertNotNull(afterProcessing.getProcessedAt());
        assertNotNull(afterProcessing.getProcessedS3Key());
        assertNotNull(afterProcessing.getTotalChunks());
        assertTrue(afterProcessing.getTotalChunks() > 0);
        System.out.println("✅ Document status updated to PROCESSED");
        System.out.println("   - Processed at: " + afterProcessing.getProcessedAt());
        System.out.println("   - Processed S3 key: " + afterProcessing.getProcessedS3Key());
        System.out.println("   - Total chunks: " + afterProcessing.getTotalChunks());
        
        // Verify chunks were created in MongoDB
        System.out.println("🔍 Step 5: Verifying chunks in MongoDB...");
        Long chunkCount = chunkRepository.countByDocumentId(documentId).block();
        assertNotNull(chunkCount);
        assertEquals(afterProcessing.getTotalChunks(), chunkCount.intValue());
        assertTrue(chunkCount > 0);
        System.out.println("✅ Found " + chunkCount + " chunks in MongoDB");
        
        // Verify chunk details
        StepVerifier.create(chunkRepository.findByDocumentId(documentId))
                .expectNextCount(chunkCount)
                .verifyComplete();
        
        // Verify each chunk has required fields
        Flux<KnowledgeHubChunk> chunks = chunkRepository.findByDocumentId(documentId);
        StepVerifier.create(chunks)
                .thenConsumeWhile(chunk -> {
                    assertNotNull(chunk.getChunkId());
                    assertEquals(documentId, chunk.getDocumentId());
                    assertEquals(TEST_TEAM_ID, chunk.getTeamId());
                    assertNotNull(chunk.getText());
                    assertFalse(chunk.getText().trim().isEmpty());
                    assertNotNull(chunk.getTokenCount());
                    assertTrue(chunk.getTokenCount() > 0);
                    assertNotNull(chunk.getChunkIndex());
                    assertNotNull(chunk.getCreatedAt());
                    assertEquals("sentence", chunk.getChunkStrategy());
                    // Verify encryption key version is set (even if using no-op encryption for tests)
                    assertNotNull(chunk.getEncryptionKeyVersion(), "Encryption key version should be set");
                    return true;
                })
                .verifyComplete();
        
        System.out.println("✅ All chunks have required fields");
        
        // Verify processed JSON exists in S3
        System.out.println("🔍 Step 6: Verifying processed JSON in S3...");
        String processedS3Key = afterProcessing.getProcessedS3Key();
        StepVerifier.create(s3Service.fileExists(processedS3Key))
                .expectNext(true)
                .verifyComplete();
        System.out.println("✅ Processed JSON exists in S3: " + processedS3Key);
        
        // Download and verify processed JSON structure
        byte[] processedJsonBytes = s3Service.downloadFileContent(processedS3Key).block();
        assertNotNull(processedJsonBytes);
        assertTrue(processedJsonBytes.length > 0);
        String processedJson = new String(processedJsonBytes, StandardCharsets.UTF_8);
        assertTrue(processedJson.contains("\"documentId\""));
        assertTrue(processedJson.contains("\"chunks\""));
        assertTrue(processedJson.contains("\"statistics\""));
        System.out.println("✅ Processed JSON is valid and contains expected fields");
        System.out.println("   - JSON size: " + processedJsonBytes.length + " bytes");
        
        // Cleanup
        System.out.println("🧹 Cleaning up test data...");
        cleanupTestData(documentId, s3Key, processedS3Key);
        
        System.out.println("🎉 Full document processing integration test passed!");
    }
    
    @Test
    void testProcessDocument_ExistingUSCISDocument() {
        // Test processing with an existing USCIS form document that's already uploaded to S3
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Skip if document ID is not configured
        if ("YOUR_EXISTING_DOCUMENT_ID_HERE".equals(EXISTING_USCIS_DOCUMENT_ID)) {
            System.out.println("⚠️  Skipping test - EXISTING_USCIS_DOCUMENT_ID not configured");
            return;
        }
        
        // Use dev bucket for existing USCIS document
        String devBucketName = "linqra-knowledge-hub-dev";
        S3Properties devS3Properties = new S3Properties();
        devS3Properties.setBucketName(devBucketName);
        devS3Properties.setRawPrefix("raw");
        devS3Properties.setProcessedPrefix("processed");
        devS3Properties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        devS3Properties.setMaxFileSize(52428800L);
        
        // Setup S3 service with dev bucket
        S3ServiceImpl devS3Service = setupS3ServiceForBucket(devS3Properties);
        
        // Setup processing service with dev bucket
        TikaDocumentParser tikaDocumentParser = new TikaDocumentParser();
        ChunkingService chunkingService = new ChunkingService();
        
        // Create a no-op MessageChannel for testing (WebSocket publishing not needed in integration tests)
        MessageChannel mockExecutionMessageChannel = new AbstractMessageChannel() {
            @Override
            protected boolean sendInternal(@NonNull Message<?> message, long timeout) {
                // No-op: just return true to simulate successful message sending
                return true;
            }
        };
        
        // Create a no-op ApplicationEventPublisher for testing (events not needed in integration tests)
        org.springframework.context.ApplicationEventPublisher mockEventPublisher = event -> {
            // No-op: just log that event would be published
            // log.debug("Event published: {}", event.getClass().getSimpleName());
        };
        
        // Use the same encryption service (no-op if not autowired)
        ChunkEncryptionService encryptionService = chunkEncryptionService != null 
            ? chunkEncryptionService 
            : createNoOpEncryptionService();
        
        KnowledgeHubDocumentProcessingServiceImpl devDocumentProcessingService = new KnowledgeHubDocumentProcessingServiceImpl(
                documentRepository,
                chunkRepository,
                devS3Service,
                tikaDocumentParser,
                chunkingService,
                devS3Properties,
                mockEventPublisher,
                encryptionService,
                mockExecutionMessageChannel
        );
        
        System.out.println("📦 Using S3 bucket: " + devBucketName);
        
        // Step 1: Get existing document from MongoDB
        System.out.println("📋 Step 1: Retrieving existing document from MongoDB...");
        System.out.println("   - Document ID: " + EXISTING_USCIS_DOCUMENT_ID);
        
        KnowledgeHubDocument existingDocument = documentRepository.findByDocumentId(EXISTING_USCIS_DOCUMENT_ID).block();
        
        if (existingDocument == null) {
            System.out.println("❌ Document not found in MongoDB. Please check the documentId: " + EXISTING_USCIS_DOCUMENT_ID);
            fail("Document not found: " + EXISTING_USCIS_DOCUMENT_ID);
            return;
        }
        
        String documentId = existingDocument.getDocumentId();
        String collectionId = existingDocument.getCollectionId();
        String fileName = existingDocument.getFileName();
        String s3Key = existingDocument.getS3Key();
        String teamId = existingDocument.getTeamId();
        
        assertNotNull(collectionId, "Collection ID should not be null");
        assertNotNull(fileName, "File name should not be null");
        assertNotNull(s3Key, "S3 key should not be null");
        assertNotNull(teamId, "Team ID should not be null");
        
        System.out.println("✅ Document retrieved successfully");
        System.out.println("   - Collection ID: " + collectionId);
        System.out.println("   - File Name: " + fileName);
        System.out.println("   - S3 Key: " + s3Key);
        System.out.println("   - Team ID: " + teamId);
        System.out.println("   - Current Status: " + existingDocument.getStatus());
        System.out.println("   - File Size: " + existingDocument.getFileSize() + " bytes");
        
        // Step 2: Verify file exists in S3
        System.out.println("🔍 Step 2: Verifying file exists in S3...");
        Boolean fileExists = devS3Service.fileExists(s3Key).block();
        
        if (!Boolean.TRUE.equals(fileExists)) {
            System.out.println("❌ File does not exist in S3: " + s3Key);
            fail("File not found in S3: " + s3Key);
            return;
        }
        
        System.out.println("✅ File exists in S3: " + s3Key);
        
        // Step 3: Ensure document status is UPLOADED (update if needed)
        System.out.println("🔍 Step 3: Verifying document status...");
        
        if (!"UPLOADED".equals(existingDocument.getStatus()) && !"PARSING".equals(existingDocument.getStatus())) {
            System.out.println("⚠️  Document status is: " + existingDocument.getStatus() + ". Updating to UPLOADED for processing...");
            existingDocument.setStatus("UPLOADED");
            documentRepository.save(existingDocument).block();
            System.out.println("✅ Document status updated to UPLOADED");
        } else {
            System.out.println("✅ Document status is ready for processing: " + existingDocument.getStatus());
        }
        
        // Step 4: Clear any existing chunks (if reprocessing)
        System.out.println("🧹 Step 4: Clearing any existing chunks for reprocessing...");
        Long existingChunkCount = chunkRepository.countByDocumentId(documentId).block();
        if (existingChunkCount != null && existingChunkCount > 0) {
            System.out.println("   - Found " + existingChunkCount + " existing chunks, deleting...");
            chunkRepository.deleteAllByDocumentId(documentId).block();
            System.out.println("✅ Existing chunks deleted");
        } else {
            System.out.println("✅ No existing chunks found");
        }
        
        // Step 5: Process the document
        System.out.println("⚙️  Step 5: Processing existing USCIS form document...");
        long startTime = System.currentTimeMillis();
        
        StepVerifier.create(devDocumentProcessingService.processDocument(documentId, teamId))
                .verifyComplete();
        
        long processingTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Document processing completed in " + processingTime + " ms");
        
        // Step 6: Verify document status changed to PROCESSED
        System.out.println("🔍 Step 6: Verifying document status...");
        KnowledgeHubDocument afterProcessing = documentRepository.findByDocumentId(documentId).block();
        assertNotNull(afterProcessing);
        assertEquals("PROCESSED", afterProcessing.getStatus());
        assertNotNull(afterProcessing.getProcessedAt());
        assertNotNull(afterProcessing.getProcessedS3Key());
        assertNotNull(afterProcessing.getTotalChunks());
        assertTrue(afterProcessing.getTotalChunks() > 0);
        System.out.println("✅ Document status updated to PROCESSED");
        System.out.println("   - Processed at: " + afterProcessing.getProcessedAt());
        System.out.println("   - Processed S3 key: " + afterProcessing.getProcessedS3Key());
        System.out.println("   - Total chunks: " + afterProcessing.getTotalChunks());
        System.out.println("   - Total tokens: " + afterProcessing.getTotalTokens());
        
        // Step 7: Verify chunks were created in MongoDB
        System.out.println("🔍 Step 7: Verifying chunks in MongoDB...");
        Long chunkCount = chunkRepository.countByDocumentId(documentId).block();
        assertNotNull(chunkCount);
        assertEquals(afterProcessing.getTotalChunks(), chunkCount.intValue());
        assertTrue(chunkCount > 0);
        System.out.println("✅ Found " + chunkCount + " chunks in MongoDB");
        
        // Verify chunk details
        StepVerifier.create(chunkRepository.findByDocumentId(documentId))
                .expectNextCount(chunkCount)
                .verifyComplete();
        
        // Verify each chunk has required fields
        Flux<KnowledgeHubChunk> chunks = chunkRepository.findByDocumentId(documentId);
        StepVerifier.create(chunks)
                .thenConsumeWhile(chunk -> {
                    assertNotNull(chunk.getChunkId());
                    assertEquals(documentId, chunk.getDocumentId());
                    assertEquals(teamId, chunk.getTeamId());
                    assertNotNull(chunk.getText());
                    assertFalse(chunk.getText().trim().isEmpty());
                    assertNotNull(chunk.getTokenCount());
                    assertTrue(chunk.getTokenCount() > 0);
                    assertNotNull(chunk.getChunkIndex());
                    assertNotNull(chunk.getCreatedAt());
                    assertNotNull(chunk.getChunkStrategy());
                    // Verify encryption key version is set (even if using no-op encryption for tests)
                    assertNotNull(chunk.getEncryptionKeyVersion(), "Encryption key version should be set");
                    return true;
                })
                .verifyComplete();
        
        System.out.println("✅ All chunks have required fields");
        
        // Step 8: Verify processed JSON exists in S3
        System.out.println("🔍 Step 8: Verifying processed JSON in S3...");
        String processedS3Key = afterProcessing.getProcessedS3Key();
        StepVerifier.create(devS3Service.fileExists(processedS3Key))
                .expectNext(true)
                .verifyComplete();
        System.out.println("✅ Processed JSON exists in S3: " + processedS3Key);
        
        // Download and verify processed JSON structure
        byte[] processedJsonBytes = devS3Service.downloadFileContent(processedS3Key).block();
        assertNotNull(processedJsonBytes);
        assertTrue(processedJsonBytes.length > 0);
        String processedJson = new String(processedJsonBytes, StandardCharsets.UTF_8);
        assertTrue(processedJson.contains("\"documentId\""));
        assertTrue(processedJson.contains("\"chunks\""));
        assertTrue(processedJson.contains("\"statistics\""));
        System.out.println("✅ Processed JSON is valid and contains expected fields");
        System.out.println("   - JSON size: " + processedJsonBytes.length + " bytes");
        
        // Print summary
        System.out.println("\n📊 Processing Summary:");
        System.out.println("   - Document: " + fileName);
        System.out.println("   - Collection: " + collectionId);
        System.out.println("   - Processing time: " + processingTime + " ms");
        System.out.println("   - Total chunks: " + chunkCount);
        System.out.println("   - Total tokens: " + afterProcessing.getTotalTokens());
        System.out.println("   - Chunk strategy: " + afterProcessing.getChunkStrategy());
        
        // Cleanup (if configured)
        System.out.println("\n🧹 Cleaning up test data...");
        cleanupTestDataWithBucket(documentId, s3Key, processedS3Key, devS3Service, devBucketName);
        
        System.out.println("🎉 Existing USCIS document processing test passed!");
    }
    
    @Test
    void testProcessDocument_WithTokenChunking() {
        // Test processing with token-based chunking strategy
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Given - Create a document with token chunking strategy
        String documentId = UUID.randomUUID().toString();
        String fileName = "token-chunking-test.txt";
        String s3Key = "raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/" + documentId + "_" + fileName;
        
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(documentId)
                .fileName(fileName)
                .collectionId(TEST_COLLECTION_ID)
                .contentType("text/plain")
                .s3Key(s3Key)
                .status("UPLOADED")
                .teamId(TEST_TEAM_ID)
                .chunkSize(100) // Smaller chunks for testing
                .overlapTokens(10)
                .chunkStrategy("token")
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        System.out.println("📝 Creating document with token chunking strategy...");
        documentRepository.save(document).block();
        
        // Upload test content
        String testContent = "Token one token two token three token four token five. " +
                            "Token six token seven token eight token nine token ten. " +
                            "Token eleven token twelve token thirteen token fourteen token fifteen. " +
                            "Token sixteen token seventeen token eighteen token nineteen token twenty.";
        byte[] testData = testContent.getBytes(StandardCharsets.UTF_8);
        document.setFileSize((long) testData.length);
        documentRepository.save(document).block();
        
        var dataBuffer = new DefaultDataBufferFactory().wrap(testData);
        s3Service.uploadFile(s3Key, Flux.just(dataBuffer), "text/plain", testData.length).block();
        
        // When - Process the document
        System.out.println("⚙️  Processing document with token chunking...");
        StepVerifier.create(documentProcessingService.processDocument(documentId, TEST_TEAM_ID))
                .verifyComplete();
        
        // Then - Verify chunks were created
        Long chunkCount = chunkRepository.countByDocumentId(documentId).block();
        assertNotNull(chunkCount);
        assertTrue(chunkCount > 0);
        
        // Verify all chunks use token strategy
        StepVerifier.create(chunkRepository.findByDocumentId(documentId))
                .thenConsumeWhile(chunk -> {
                    assertEquals("token", chunk.getChunkStrategy());
                    return true;
                })
                .verifyComplete();
        
        System.out.println("✅ Token chunking test passed with " + chunkCount + " chunks");
        
        // Cleanup
        cleanupTestData(documentId, s3Key, null);
    }
    
    @Test
    void testProcessDocument_DocumentNotFound() {
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        String nonExistentDocumentId = UUID.randomUUID().toString();
        
        System.out.println("🔍 Testing with non-existent document: " + nonExistentDocumentId);
        
        StepVerifier.create(documentProcessingService.processDocument(nonExistentDocumentId, TEST_TEAM_ID))
                .expectErrorMatches(throwable -> {
                    assertInstanceOf(RuntimeException.class, throwable);
                    assertTrue(throwable.getMessage().contains("Document not found"));
                    System.out.println("✅ Correctly returned error for non-existent document");
                    return true;
                })
                .verify();
    }
    
    @Test
    void testProcessDocument_AccessDenied() {
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Given - Create a document for a different team
        String otherTeamId = "other-team-id-12345";
        String documentId = UUID.randomUUID().toString();
        String fileName = "access-denied-test.txt";
        String s3Key = "raw/" + otherTeamId + "/test-collection/" + documentId + "_" + fileName;
        
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(documentId)
                .fileName(fileName)
                .collectionId("test-collection")
                .contentType("text/plain")
                .s3Key(s3Key)
                .status("UPLOADED")
                .teamId(otherTeamId)
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        documentRepository.save(document).block();
        
        System.out.println("🔍 Testing access control with different team ID...");
        
        // When - Try to process with wrong team ID
        StepVerifier.create(documentProcessingService.processDocument(documentId, TEST_TEAM_ID))
                .expectErrorMatches(throwable -> {
                    assertInstanceOf(RuntimeException.class, throwable);
                    assertTrue(throwable.getMessage().contains("Document access denied"));
                    System.out.println("✅ Correctly denied access for different team");
                    return true;
                })
                .verify();
        
        // Cleanup
        documentRepository.delete(document).block();
    }
    
    @Test
    void testProcessDocument_ErrorHandling() {
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // Given - Create a document with invalid S3 key (file doesn't exist)
        String documentId = UUID.randomUUID().toString();
        String fileName = "missing-file-test.txt";
        String invalidS3Key = "raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/nonexistent/" + documentId + "_" + fileName;
        
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(documentId)
                .fileName(fileName)
                .collectionId(TEST_COLLECTION_ID)
                .contentType("text/plain")
                .s3Key(invalidS3Key)
                .status("UPLOADED")
                .teamId(TEST_TEAM_ID)
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        documentRepository.save(document).block();
        
        System.out.println("🔍 Testing error handling with missing S3 file...");
        
        // When - Process the document (should fail because file doesn't exist)
        StepVerifier.create(documentProcessingService.processDocument(documentId, TEST_TEAM_ID))
                .verifyComplete(); // The error is handled internally and status is set to FAILED
        
        // Then - Verify document status is FAILED
        KnowledgeHubDocument failedDocument = documentRepository.findByDocumentId(documentId).block();
        assertNotNull(failedDocument);
        assertEquals("FAILED", failedDocument.getStatus());
        assertNotNull(failedDocument.getErrorMessage());
        System.out.println("✅ Document status correctly set to FAILED");
        System.out.println("   - Error message: " + failedDocument.getErrorMessage());
        
        // Cleanup
        cleanupTestData(documentId, invalidS3Key, null);
    }
    
    @Test
    void testDownloadProcessedJson_FromDevBucket() {
        // Test downloading processed JSON for a specific document from dev bucket
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping integration test - AWS credentials not set");
            return;
        }
        
        // TODO: Replace with your actual documentId that has a processed JSON file
        String documentId = "b5b5987a-5e7b-4f7f-a1fe-696b70bea7ce";
        
        // Use dev bucket
        String devBucketName = "linqra-knowledge-hub-dev";
        S3Properties devS3Properties = new S3Properties();
        devS3Properties.setBucketName(devBucketName);
        devS3Properties.setRawPrefix("raw");
        devS3Properties.setProcessedPrefix("processed");
        devS3Properties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        devS3Properties.setMaxFileSize(52428800L);
        
        // Setup S3 service with dev bucket
        S3ServiceImpl devS3Service = setupS3ServiceForBucket(devS3Properties);
        
        System.out.println("📦 Using S3 bucket: " + devBucketName);
        System.out.println("📋 Testing download from S3 for document: " + documentId);
        
        // Step 1: Get document from MongoDB
        System.out.println("🔍 Step 1: Retrieving document from MongoDB...");
        KnowledgeHubDocument document = documentRepository.findByDocumentId(documentId).block();
        
        if (document == null) {
            System.out.println("❌ Document not found in MongoDB: " + documentId);
            fail("Document not found: " + documentId);
            return;
        }
        
        System.out.println("✅ Document found:");
        System.out.println("   - Document ID: " + document.getDocumentId());
        System.out.println("   - File Name: " + document.getFileName());
        System.out.println("   - Status: " + document.getStatus());
        System.out.println("   - Raw S3 Key: " + document.getS3Key());
        System.out.println("   - Processed S3 Key: " + document.getProcessedS3Key());
        
        // Step 2: Try to download processed JSON if available
        String processedS3Key = document.getProcessedS3Key();
        if (processedS3Key != null && !processedS3Key.isEmpty()) {
            System.out.println("\n🔍 Step 2: Testing processed JSON download...");
            System.out.println("   - Processed S3 Key: " + processedS3Key);
            
            Boolean fileExists = devS3Service.fileExists(processedS3Key).block();
            
            if (!Boolean.TRUE.equals(fileExists)) {
                System.out.println("❌ Processed JSON file does not exist in S3: " + processedS3Key);
            } else {
                System.out.println("✅ Processed JSON file exists in S3");
                
                // Try to download
                try {
                    byte[] processedJsonBytes = devS3Service.downloadFileContent(processedS3Key).block();
                    
                    if (processedJsonBytes == null || processedJsonBytes.length == 0) {
                        System.out.println("❌ Downloaded processed JSON is empty");
                    } else {
                        System.out.println("✅ Successfully downloaded processed JSON!");
                        System.out.println("   - File size: " + processedJsonBytes.length + " bytes");
                        
                        // Verify JSON structure
                        String processedJson = new String(processedJsonBytes, StandardCharsets.UTF_8);
                        assertTrue(processedJson.contains("\"documentId\""), "JSON should contain documentId field");
                        assertTrue(processedJson.contains("\"chunks\""), "JSON should contain chunks field");
                        assertTrue(processedJson.contains("\"statistics\""), "JSON should contain statistics field");
                        
                        System.out.println("✅ Processed JSON structure is valid");
                        System.out.println("🎉 Successfully downloaded and verified processed JSON!");
                        return; // Success - exit early
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error downloading processed JSON:");
                    System.err.println("   - Error type: " + e.getClass().getName());
                    System.err.println("   - Error message: " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("   - Cause: " + e.getCause().getClass().getName());
                        System.err.println("   - Cause message: " + e.getCause().getMessage());
                    }
                    e.printStackTrace();
                    System.out.println("⚠️  Processed JSON download failed, will try raw file instead");
                }
            }
        } else {
            System.out.println("\n⚠️  Document does not have a processedS3Key. Status: " + document.getStatus());
            System.out.println("   - Will test downloading raw file instead");
        }
        
        // Step 3: Test downloading raw file (since processed JSON is not available)
        String rawS3Key = document.getS3Key();
        if (rawS3Key == null || rawS3Key.isEmpty()) {
            System.out.println("❌ Document does not have a raw S3 key");
            fail("Document has no S3 keys to test");
            return;
        }
        
        System.out.println("\n🔍 Step 3: Testing raw file download...");
        System.out.println("   - Raw S3 Key: " + rawS3Key);
        
        // Check if raw file exists
        Boolean rawFileExists = devS3Service.fileExists(rawS3Key).block();
        
        if (!Boolean.TRUE.equals(rawFileExists)) {
            System.out.println("❌ Raw file does not exist in S3: " + rawS3Key);
            fail("Raw file not found in S3: " + rawS3Key);
            return;
        }
        
        System.out.println("✅ Raw file exists in S3");
        
        // Try to download the raw file
        System.out.println("⬇️  Downloading raw file from S3...");
        
        try {
            byte[] rawFileBytes = devS3Service.downloadFileContent(rawS3Key).block();
            
            if (rawFileBytes == null || rawFileBytes.length == 0) {
                System.out.println("❌ Downloaded raw file is empty");
                fail("Downloaded raw file is empty");
                return;
            }
            
            System.out.println("✅ Successfully downloaded raw file!");
            System.out.println("   - File size: " + rawFileBytes.length + " bytes");
            System.out.println("   - Expected size (from MongoDB): " + document.getFileSize() + " bytes");
            
            // Verify file size matches (within reasonable tolerance)
            if (document.getFileSize() != null) {
                long sizeDifference = Math.abs(rawFileBytes.length - document.getFileSize());
                if (sizeDifference > 1000) { // Allow 1KB difference
                    System.out.println("⚠️  File size mismatch (difference: " + sizeDifference + " bytes)");
                } else {
                    System.out.println("✅ File size matches MongoDB record");
                }
            }
            
            System.out.println("\n🎉 Successfully downloaded raw file from S3!");
            System.out.println("   - Document ID: " + documentId);
            System.out.println("   - S3 Key: " + rawS3Key);
            System.out.println("   - File size: " + rawFileBytes.length + " bytes");
            System.out.println("   - Content Type: " + document.getContentType());
            
            // Print file type detection
            if (rawFileBytes.length > 4) {
                System.out.println("   - First 4 bytes (hex): " + 
                    String.format("%02X %02X %02X %02X", 
                        rawFileBytes[0] & 0xFF, 
                        rawFileBytes[1] & 0xFF, 
                        rawFileBytes[2] & 0xFF, 
                        rawFileBytes[3] & 0xFF));
            }
            
        } catch (Exception e) {
            System.err.println("\n❌ Error downloading raw file:");
            System.err.println("   - Error type: " + e.getClass().getName());
            System.err.println("   - Error message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   - Cause: " + e.getCause().getClass().getName());
                System.err.println("   - Cause message: " + e.getCause().getMessage());
            }
            if (e.getCause() != null && e.getCause().getCause() != null) {
                System.err.println("   - Root cause: " + e.getCause().getCause().getClass().getName());
                System.err.println("   - Root cause message: " + e.getCause().getCause().getMessage());
            }
            System.err.println("\n📋 Full stack trace:");
            e.printStackTrace();
            fail("Failed to download raw file: " + e.getMessage());
        }
    }
    
    private void cleanupTestData(String documentId, String rawS3Key, String processedS3Key) {
        cleanupTestDataWithBucket(documentId, rawS3Key, processedS3Key, s3Service, BUCKET_NAME);
    }
    
    private void cleanupTestDataWithBucket(String documentId, String rawS3Key, String processedS3Key, 
                                           S3ServiceImpl s3ServiceToUse, String bucketName) {
        if (!CLEANUP_AFTER_TEST) {
            // Keep files for inspection
            System.out.println("📋 Files kept for inspection (CLEANUP_AFTER_TEST = false):");
            if (rawS3Key != null) {
                System.out.println("   - Raw file: s3://" + bucketName + "/" + rawS3Key);
            }
            if (processedS3Key != null) {
                System.out.println("   - Processed JSON: s3://" + bucketName + "/" + processedS3Key);
            }
            System.out.println("   - Document ID: " + documentId);
            System.out.println("   - MongoDB document and chunks are kept");
            return;
        }
        
        try {
            // Delete chunks
            chunkRepository.deleteAllByDocumentId(documentId).block();
            System.out.println("   - Deleted chunks from MongoDB");
            
            // Delete document
            documentRepository.findByDocumentId(documentId)
                    .flatMap(documentRepository::delete)
                    .block();
            System.out.println("   - Deleted document from MongoDB");
            
            // Delete raw file from S3
            if (rawS3Key != null) {
                s3ServiceToUse.deleteFile(rawS3Key).block();
                System.out.println("   - Deleted raw file from S3");
            }
            
            // Delete processed JSON from S3
            if (processedS3Key != null) {
                s3ServiceToUse.deleteFile(processedS3Key).block();
                System.out.println("   - Deleted processed JSON from S3");
            }
        } catch (Exception e) {
            System.err.println("⚠️  Error during cleanup: " + e.getMessage());
        }
    }
    
    private void setupRealS3Service() {
        s3Service = setupS3ServiceForBucket(s3Properties);
    }
    
    private S3ServiceImpl setupS3ServiceForBucket(S3Properties properties) {
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
        
        // Create and return S3Service
        return new S3ServiceImpl(s3AsyncClient, s3Presigner, properties);
    }
}
