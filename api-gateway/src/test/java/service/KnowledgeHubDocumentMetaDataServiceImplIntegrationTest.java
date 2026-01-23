package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.config.StorageProperties;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.enums.DocumentStatus;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.impl.KnowledgeHubDocumentMetaDataServiceImpl;

import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.impl.ObjectStorageServiceImpl;
import org.lite.gateway.util.AuditLogHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.messaging.MessageChannel;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.lite.gateway.service.LinqraVaultService;
import io.milvus.client.MilvusServiceClient;
import org.neo4j.driver.Driver;
import org.lite.gateway.service.LinqMilvusStoreService;

/**
 * Integration tests for KnowledgeHubDocumentMetaDataService using real MongoDB
 * and S3
 * 
 * WARNING: This test uses real AWS credentials and will upload to real S3
 * bucket!
 * Make sure to delete credentials after testing!
 * 
 * Prerequisites:
 * - A document must exist in MongoDB with a processedS3Key
 * - The processed JSON file must exist in S3
 * - Or use testExtractMetadata_WithRealDocumentId to test with an existing
 * document
 */
@SpringBootTest(classes = org.lite.gateway.ApiGatewayApplication.class, webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class KnowledgeHubDocumentMetaDataServiceImplIntegrationTest {

    // TODO: DELETE THESE CREDENTIALS AFTER TESTING!
    private static final String AWS_ACCESS_KEY = "YOUR_AWS_ACCESS_KEY";
    private static final String AWS_SECRET_KEY = "YOUR_AWS_SECRET_KEY";
    private static final String AWS_REGION = "us-west-2";
    private static final String BUCKET_NAME = "linqra-knowledge-hub-test"; // Use dev bucket for testing
    private static final String TEST_TEAM_ID = "67d0aeb17172416c411d419e";
    private static final String TEST_COLLECTION_ID = "test-collection-metadata";

    // Replace with an actual documentId that has processed JSON in S3
    private static final String EXISTING_DOCUMENT_ID = "9d7b8f74-889e-4e05-ae35-5c2389e39e88";

    @Autowired
    private KnowledgeHubDocumentRepository documentRepository;

    @Autowired
    private KnowledgeHubDocumentMetaDataRepository metadataRepository;

    @MockitoBean
    private AuditLogHelper auditLogHelper;

    @MockitoBean
    private ChunkEncryptionService chunkEncryptionService;

    @MockitoBean
    private LinqraVaultService linqraVaultService;

    @MockitoBean
    private MilvusServiceClient milvusServiceClient;

    @MockitoBean
    private Driver neo4jDriver;

    @MockitoBean
    private LinqMilvusStoreService linqMilvusStoreService;

    @MockitoBean(name = "executionMessageChannel")
    private MessageChannel executionMessageChannel;

    @MockitoBean
    private KnowledgeHubDocumentEmbeddingService embeddingService;

    private KnowledgeHubDocumentMetaDataServiceImpl metadataService;
    private ObjectStorageServiceImpl objectStorageService;
    private StorageProperties storageProperties;
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

        // Setup StorageProperties
        this.storageProperties = new StorageProperties();
        this.storageProperties.setBucketName(BUCKET_NAME);
        this.storageProperties.setRawPrefix("raw");
        this.storageProperties.setProcessedPrefix("processed");
        this.storageProperties.setPresignedUrlExpiration(Duration.ofMinutes(15));
        this.storageProperties.setMaxFileSize(52428800L);
        this.storageProperties.setType("s3");

        // Setup real ObjectStorage service
        setupRealObjectStorageService();

        // Setup ObjectMapper
        this.objectMapper = new ObjectMapper();

        // Configure mocks
        when(chunkEncryptionService.encryptChunkText(anyString(), anyString()))
                .thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(chunkEncryptionService.encryptChunkText(anyString(), anyString(), anyString()))
                .thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(chunkEncryptionService.decryptChunkText(anyString(), anyString(), anyString()))
                .thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(chunkEncryptionService.getCurrentKeyVersion(anyString())).thenReturn(Mono.just("v1"));

        when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), anyString(), anyString(), anyMap(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), anyString(), anyString(), anyMap(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        when(embeddingService.embedDocument(anyString(), anyString())).thenReturn(Mono.empty());

        // Create KnowledgeHubDocumentMetaDataServiceImpl
        this.metadataService = new KnowledgeHubDocumentMetaDataServiceImpl(
                metadataRepository,
                documentRepository,
                objectStorageService,
                objectMapper,
                executionMessageChannel,
                embeddingService,
                chunkEncryptionService,
                auditLogHelper);
    }

    private void setupRealObjectStorageService() {
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

        this.objectStorageService = new ObjectStorageServiceImpl(s3Client, s3Presigner, storageProperties);
    }

    /**
     * Test metadata extraction with an existing real document ID
     * Replace EXISTING_DOCUMENT_ID with an actual documentId that has processed
     * JSON in S3
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
        String processedS3Key = storageProperties.buildProcessedKey(TEST_TEAM_ID, TEST_COLLECTION_ID, testDocumentId);

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
                    .status(DocumentStatus.PROCESSED)
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

        // When document doesn't exist, the service first validates document existence
        // and throws "Document not found" before checking metadata
        StepVerifier.create(metadataService.getMetadataExtract(nonExistentDocumentId, TEST_TEAM_ID))
                .expectErrorMatches(error -> error instanceof RuntimeException &&
                        error.getMessage().contains("Document not found"))
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
                .status(DocumentStatus.UPLOADED)
                .s3Key("raw/" + TEST_TEAM_ID + "/" + TEST_COLLECTION_ID + "/" + testDocumentId + "_test.pdf")
                .processedS3Key(null) // No processed JSON
                .createdAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();

        KnowledgeHubDocument savedDocument = documentRepository.save(document).block();

        try {
            StepVerifier.create(metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID))
                    .expectErrorMatches(error -> error instanceof RuntimeException &&
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
        String processedS3Key = storageProperties.buildProcessedKey(TEST_TEAM_ID, TEST_COLLECTION_ID, testDocumentId);

        try {
            // Create test document and metadata (similar to
            // testExtractMetadata_CreateTestDocument)
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
                    .status(DocumentStatus.PROCESSED)
                    .processedS3Key(processedS3Key)
                    .createdAt(LocalDateTime.now())
                    .build();

            KnowledgeHubDocument savedDocument = documentRepository.save(document).block();

            // Extract metadata first
            metadataService.extractMetadata(testDocumentId, TEST_TEAM_ID).block();

            // Verify metadata exists
            StepVerifier.create(metadataService.getMetadataExtract(testDocumentId, TEST_TEAM_ID))
                    .assertNext(Assertions::assertNotNull)
                    .verifyComplete();

            // Delete metadata
            StepVerifier.create(metadataService.deleteMetadataExtract(testDocumentId))
                    .verifyComplete();

            // Verify metadata is deleted by checking the repository directly
            // (getMetadataExtract auto-extracts if metadata is missing, so we check
            // repository)
            StepVerifier.create(metadataRepository.findByDocumentIdAndTeamId(testDocumentId, TEST_TEAM_ID))
                    .expectNextCount(0)
                    .verifyComplete();

            System.out.println("✅ Delete metadata test successful!");

            // Cleanup
            assert savedDocument != null;
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

        // ProcessingMetadata
        Map<String, Object> processingMetadata = new HashMap<>();
        processingMetadata.put("documentId", documentId);
        processingMetadata.put("processedAt", LocalDateTime.now().toString());
        processingMetadata.put("processingVersion", "1.0");
        processingMetadata.put("status", "SUCCESS");
        processedJson.put("processingMetadata", processingMetadata);

        // Statistics
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("pageCount", 10);
        statistics.put("wordCount", 5000);
        statistics.put("characterCount", 25000);
        statistics.put("language", "en");
        statistics.put("totalChunks", 5);
        statistics.put("totalTokens", 2000);
        processedJson.put("statistics", statistics);

        // ExtractedMetadata (not "metadata")
        Map<String, Object> extractedMetadata = new HashMap<>();
        extractedMetadata.put("title", "Test Document Title");
        extractedMetadata.put("author", "Test Author");
        extractedMetadata.put("subject", "Test Subject");
        extractedMetadata.put("keywords", "test, integration, metadata");
        extractedMetadata.put("creator", "Test Creator");
        extractedMetadata.put("producer", "Test Producer");
        extractedMetadata.put("pageCount", 10);
        extractedMetadata.put("language", "en");
        processedJson.put("extractedMetadata", extractedMetadata);

        // ChunkingStrategy
        Map<String, Object> chunkingStrategy = new HashMap<>();
        chunkingStrategy.put("method", "sentence");
        chunkingStrategy.put("maxTokens", 400);
        chunkingStrategy.put("overlapTokens", 50);
        processedJson.put("chunkingStrategy", chunkingStrategy);

        // QualityChecks
        Map<String, Object> qualityChecks = new HashMap<>();
        qualityChecks.put("allChunksValid", true);
        qualityChecks.put("warnings", List.of());
        qualityChecks.put("errors", List.of());
        processedJson.put("qualityChecks", qualityChecks);

        return processedJson;
    }
}
