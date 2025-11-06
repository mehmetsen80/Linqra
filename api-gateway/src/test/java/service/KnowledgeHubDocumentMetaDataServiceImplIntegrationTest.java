package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.impl.KnowledgeHubDocumentMetaDataServiceImpl;
import org.lite.gateway.service.impl.S3ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KnowledgeHubDocumentMetaDataService using real MongoDB and S3
 * 
 * WARNING: This test uses real AWS credentials and will upload to real S3 bucket!
 * Make sure to delete credentials after testing!
 * 
 * Prerequisites:
 * - A document must exist in MongoDB with a processedS3Key
 * - The processed JSON file must exist in S3
 * - Or use testExtractMetadata_WithRealDocumentId to test with an existing document
 */
@SpringBootTest(classes = org.lite.gateway.ApiGatewayApplication.class, 
                webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class KnowledgeHubDocumentMetaDataServiceImplIntegrationTest {

    // TODO: DELETE THESE CREDENTIALS AFTER TESTING!
    private static final String AWS_ACCESS_KEY = "YOUR_AWS_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_AWS_SECRET_KEY";
    private static final String AWS_REGION = "us-west-2";
    private static final String BUCKET_NAME = "linqra-knowledge-hub-dev"; // Use dev bucket for testing
    private static final String TEST_TEAM_ID = "67d0aeb17172416c411d419e";
    private static final String TEST_COLLECTION_ID = "test-collection-metadata";
    
    // Replace with an actual documentId that has processed JSON in S3
    private static final String EXISTING_DOCUMENT_ID = "YOUR_EXISTING_DOCUMENT_ID_HERE";
    
    @Autowired
    private KnowledgeHubDocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeHubDocumentMetaDataRepository metadataRepository;
    
    private KnowledgeHubDocumentMetaDataServiceImpl metadataService;
    private S3ServiceImpl s3Service;
    private S3Properties s3Properties;
    private ObjectMapper objectMapper;
    private S3AsyncClient s3Client;
    private S3Presigner s3Presigner;
    
    @BeforeEach
    void setUp() {
        // SKIP SETUP IF CREDENTIALS ARE NOT SET
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
        
        // Setup ObjectMapper
        this.objectMapper = new ObjectMapper();
        
        // Create a no-op MessageChannel for testing (WebSocket publishing not needed in integration tests)
        MessageChannel mockExecutionMessageChannel = new AbstractMessageChannel() {
            @Override
            protected boolean sendInternal(@NonNull Message<?> message, long timeout) {
                // No-op: just return true to simulate successful message sending
                return true;
            }
        };
        
        // Create KnowledgeHubDocumentMetaDataServiceImpl
        this.metadataService = new KnowledgeHubDocumentMetaDataServiceImpl(
                metadataRepository,
                documentRepository,
                s3Service,
                objectMapper,
                mockExecutionMessageChannel
        );
    }
    
    private void setupRealS3Service() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(AWS_ACCESS_KEY, AWS_SECRET_KEY);
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(awsCredentials);
        
        this.s3Client = S3AsyncClient.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(credentialsProvider)
                .build();
        
        this.s3Presigner = S3Presigner.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(credentialsProvider)
                .build();
        
        this.s3Service = new S3ServiceImpl(s3Client, s3Presigner, s3Properties);
    }
    
    /**
     * Test metadata extraction with an existing real document ID
     * Replace EXISTING_DOCUMENT_ID with an actual documentId that has processed JSON in S3
     */
    @Test
    void testExtractMetadata_WithRealDocumentId() {
        // SKIP IF CREDENTIALS OR DOCUMENT ID NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY) ||
            "YOUR_EXISTING_DOCUMENT_ID_HERE".equals(EXISTING_DOCUMENT_ID)) {
            System.out.println("⚠️  Skipping test - AWS credentials or document ID not set");
            return;
        }
        
        System.out.println("📋 Testing metadata extraction for document: " + EXISTING_DOCUMENT_ID);
        System.out.println("   Team ID: " + TEST_TEAM_ID);
        
        // Extract metadata
        StepVerifier.create(metadataService.extractMetadata(EXISTING_DOCUMENT_ID, TEST_TEAM_ID))
                .assertNext(metadata -> {
                    assertNotNull(metadata);
                    assertNotNull(metadata.getId());
                    assertEquals(EXISTING_DOCUMENT_ID, metadata.getDocumentId());
                    assertEquals(TEST_TEAM_ID, metadata.getTeamId());
                    assertEquals("EXTRACTED", metadata.getStatus());
                    assertNotNull(metadata.getExtractedAt());
                    
                    System.out.println("✅ Metadata extracted successfully!");
                    System.out.println("   - Document ID: " + metadata.getDocumentId());
                    System.out.println("   - Title: " + metadata.getTitle());
                    System.out.println("   - Author: " + metadata.getAuthor());
                    System.out.println("   - Page Count: " + metadata.getPageCount());
                    System.out.println("   - Word Count: " + metadata.getWordCount());
                    System.out.println("   - Document Type: " + metadata.getDocumentType());
                    System.out.println("   - Language: " + metadata.getLanguage());
                    
                    // Verify metadata fields were extracted
                    assertNotNull(metadata.getDocumentType());
                    assertNotNull(metadata.getMimeType());
                })
                .verifyComplete();
        
        // Test retrieving the extracted metadata
        StepVerifier.create(metadataService.getMetadataExtract(EXISTING_DOCUMENT_ID, TEST_TEAM_ID))
                .assertNext(metadata -> {
                    assertNotNull(metadata);
                    assertEquals(EXISTING_DOCUMENT_ID, metadata.getDocumentId());
                    System.out.println("✅ Metadata retrieval successful!");
                })
                .verifyComplete();
    }
    
    /**
     * Test metadata extraction by creating a test document with processed JSON
     */
    @Test
    void testExtractMetadata_CreateTestDocument() {
        // SKIP IF CREDENTIALS NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping test - AWS credentials not set");
            return;
        }
        
        System.out.println("📋 Creating test document with processed JSON...");
        
        // Step 1: Create a test document in MongoDB
        String testDocumentId = UUID.randomUUID().toString();
        String processedS3Key = s3Properties.buildProcessedKey(TEST_TEAM_ID, TEST_COLLECTION_ID, testDocumentId);
        
        // Create processed JSON content
        Map<String, Object> processedJson = createTestProcessedJson(testDocumentId);
        
        try {
            // Step 2: Upload processed JSON to S3
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedJson);
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(processedS3Key)
                    .contentType("application/json")
                    .build();
            
            s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(jsonBytes)).join();
            System.out.println("✅ Uploaded processed JSON to S3: " + processedS3Key);
            
            // Step 3: Create document record in MongoDB
            KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                    .documentId(testDocumentId)
                    .fileName("test-document.pdf")
                    .collectionId(TEST_COLLECTION_ID)
                    .teamId(TEST_TEAM_ID)
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .status("PROCESSED")
                    .s3Key("raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/" + testDocumentId + "_test.pdf")
                    .processedS3Key(processedS3Key)
                    .chunkSize(400)
                    .overlapTokens(50)
                    .chunkStrategy("sentence")
                    .totalChunks(5)
                    .totalTokens(2000L)
                    .createdAt(LocalDateTime.now())
                    .uploadedAt(LocalDateTime.now())
                    .processedAt(LocalDateTime.now())
                    .build();
            
            KnowledgeHubDocument savedDocument = documentRepository.save(document).block();
            assertNotNull(savedDocument);
            System.out.println("✅ Created document record: " + savedDocument.getId());
            
            // Step 4: Extract metadata
            System.out.println("📋 Extracting metadata...");
            StepVerifier.create(metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID))
                    .assertNext(metadata -> {
                        assertNotNull(metadata);
                        assertEquals(testDocumentId, metadata.getDocumentId());
                        assertEquals(TEST_TEAM_ID, metadata.getTeamId());
                        assertEquals(TEST_COLLECTION_ID, metadata.getCollectionId());
                        assertEquals("EXTRACTED", metadata.getStatus());
                        assertEquals("PDF", metadata.getDocumentType());
                        assertEquals("application/pdf", metadata.getMimeType());
                        
                        // Verify extracted statistics
                        assertEquals(10, metadata.getPageCount());
                        assertEquals(5000, metadata.getWordCount());
                        assertEquals(25000, metadata.getCharacterCount());
                        assertEquals("en", metadata.getLanguage());
                        
                        // Verify extracted metadata
                        assertEquals("Test Document Title", metadata.getTitle());
                        assertEquals("Test Author", metadata.getAuthor());
                        assertEquals("Test Subject", metadata.getSubject());
                        
                        // Verify custom metadata
                        assertNotNull(metadata.getCustomMetadata());
                        assertEquals(5, metadata.getCustomMetadata().get("totalChunks"));
                        assertEquals(2000L, metadata.getCustomMetadata().get("totalTokens"));
                        assertEquals("sentence", metadata.getCustomMetadata().get("chunkStrategy"));
                        
                        System.out.println("✅ Metadata extraction successful!");
                        System.out.println("   - Title: " + metadata.getTitle());
                        System.out.println("   - Author: " + metadata.getAuthor());
                        System.out.println("   - Page Count: " + metadata.getPageCount());
                        System.out.println("   - Word Count: " + metadata.getWordCount());
                    })
                    .verifyComplete();
            
            // Step 5: Test retrieving metadata
            System.out.println("📋 Testing metadata retrieval...");
            StepVerifier.create(metadataService.getMetadataExtract(testDocumentId, TEST_TEAM_ID))
                    .assertNext(metadata -> {
                        assertNotNull(metadata);
                        assertEquals(testDocumentId, metadata.getDocumentId());
                        assertEquals("Test Document Title", metadata.getTitle());
                        System.out.println("✅ Metadata retrieval successful!");
                    })
                    .verifyComplete();
            
            // Step 6: Test updating metadata (extract again)
            System.out.println("📋 Testing metadata update (extract again)...");
            StepVerifier.create(metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID))
                    .assertNext(metadata -> {
                        assertNotNull(metadata);
                        assertEquals("EXTRACTED", metadata.getStatus());
                        assertNotNull(metadata.getExtractedAt());
                        System.out.println("✅ Metadata update successful!");
                    })
                    .verifyComplete();
            
            // Cleanup
            System.out.println("🧹 Cleaning up...");
            metadataRepository.deleteByDocumentId(testDocumentId).block();
            documentRepository.deleteById(savedDocument.getId()).block();
            s3Client.deleteObject(b -> b.bucket(BUCKET_NAME).key(processedS3Key).build()).join();
            System.out.println("✅ Cleanup completed");
            
        } catch (Exception e) {
            System.err.println("❌ Error in test: " + e.getMessage());
            e.printStackTrace();
            fail("Test failed: " + e.getMessage());
        }
    }
    
    /**
     * Test getMetadataExtract with non-existent document
     */
    @Test
    void testGetMetadataExtract_NotFound() {
        String nonExistentDocumentId = UUID.randomUUID().toString();
        
        StepVerifier.create(metadataService.getMetadataExtract(nonExistentDocumentId, TEST_TEAM_ID))
                .expectErrorMatches(error -> 
                    error instanceof RuntimeException && 
                    error.getMessage().contains("Metadata extract not found"))
                .verify();
    }
    
    /**
     * Test extractMetadata with document that has no processed JSON
     */
    @Test
    void testExtractMetadata_NoProcessedJson() {
        // SKIP IF CREDENTIALS NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping test - AWS credentials not set");
            return;
        }
        
        String testDocumentId = UUID.randomUUID().toString();
        
        // Create document without processedS3Key
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(testDocumentId)
                .fileName("test-document.pdf")
                .collectionId(TEST_COLLECTION_ID)
                .teamId(TEST_TEAM_ID)
                .fileSize(1024L)
                .contentType("application/pdf")
                .status("UPLOADED")
                .s3Key("raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/" + testDocumentId + "_test.pdf")
                .processedS3Key(null) // No processed JSON
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();
        
        KnowledgeHubDocument savedDocument = documentRepository.save(document).block();
        
        try {
            StepVerifier.create(metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID))
                    .expectErrorMatches(error -> 
                        error instanceof RuntimeException && 
                        error.getMessage().contains("Document has no processed JSON"))
                    .verify();
        } finally {
            // Cleanup
            documentRepository.deleteById(savedDocument.getId()).block();
        }
    }
    
    /**
     * Test deleteMetadataExtract
     */
    @Test
    void testDeleteMetadataExtract() {
        // SKIP IF CREDENTIALS NOT SET
        if ("YOUR_AWS_ACCESS_KEY".equals(AWS_ACCESS_KEY) || 
            "YOUR_AWS_SECRET_KEY".equals(AWS_SECRET_KEY)) {
            System.out.println("⚠️  Skipping test - AWS credentials not set");
            return;
        }
        
        String testDocumentId = UUID.randomUUID().toString();
        String processedS3Key = s3Properties.buildProcessedKey(TEST_TEAM_ID, TEST_COLLECTION_ID, testDocumentId);
        
        try {
            // Create test document and metadata (similar to testExtractMetadata_CreateTestDocument)
            Map<String, Object> processedJson = createTestProcessedJson(testDocumentId);
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(processedJson);
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(processedS3Key)
                    .contentType("application/json")
                    .build();
            
            s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(jsonBytes)).join();
            
            KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                    .documentId(testDocumentId)
                    .fileName("test-document.pdf")
                    .collectionId(TEST_COLLECTION_ID)
                    .teamId(TEST_TEAM_ID)
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .status("PROCESSED")
                    .processedS3Key(processedS3Key)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            KnowledgeHubDocument savedDocument = documentRepository.save(document).block();
            
            // Extract metadata first
            metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID).block();
            
            // Verify metadata exists
            StepVerifier.create(metadataService.getMetadataExtract(testDocumentId, TEST_TEAM_ID))
                    .assertNext(metadata -> assertNotNull(metadata))
                    .verifyComplete();
            
            // Delete metadata
            StepVerifier.create(metadataService.deleteMetadataExtract(testDocumentId))
                    .verifyComplete();
            
            // Verify metadata is deleted
            StepVerifier.create(metadataService.getMetadataExtract(testDocumentId, TEST_TEAM_ID))
                    .expectErrorMatches(error -> 
                        error instanceof RuntimeException && 
                        error.getMessage().contains("Metadata extract not found"))
                    .verify();
            
            System.out.println("✅ Delete metadata test successful!");
            
            // Cleanup
            documentRepository.deleteById(savedDocument.getId()).block();
            s3Client.deleteObject(b -> b.bucket(BUCKET_NAME).key(processedS3Key).build()).join();
            
        } catch (Exception e) {
            System.err.println("❌ Error in test: " + e.getMessage());
            e.printStackTrace();
            fail("Test failed: " + e.getMessage());
        }
    }
    
    /**
     * Create a test processed JSON structure
     */
    private Map<String, Object> createTestProcessedJson(String documentId) {
        Map<String, Object> processedJson = new HashMap<>();
        processedJson.put("documentId", documentId);
        
        // Statistics
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("pageCount", 10);
        statistics.put("wordCount", 5000);
        statistics.put("characterCount", 25000);
        statistics.put("language", "en");
        processedJson.put("statistics", statistics);
        
        // Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "Test Document Title");
        metadata.put("author", "Test Author");
        metadata.put("subject", "Test Subject");
        metadata.put("keywords", "test, integration, metadata");
        metadata.put("creator", "Test Creator");
        metadata.put("producer", "Test Producer");
        processedJson.put("metadata", metadata);
        
        // Custom metadata
        Map<String, Object> customMetadata = new HashMap<>();
        customMetadata.put("customField1", "customValue1");
        processedJson.put("customMetadata", customMetadata);
        
        return processedJson;
    }
}

