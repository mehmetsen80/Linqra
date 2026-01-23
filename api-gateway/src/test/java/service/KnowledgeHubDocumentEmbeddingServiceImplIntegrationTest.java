package service; // Refactored for ObjectStorage

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.ApiGatewayApplication;
import org.lite.gateway.config.StorageProperties;
import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.lite.gateway.service.LinqraVaultService;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.util.AuditLogHelper;
import org.neo4j.driver.Driver;
import reactor.core.publisher.Mono;

/**
 * Integration test that exercises the full document → Milvus embedding flow
 * using real services.
 *
 * <p>
 * <strong>Important:</strong> This test requires real connections to AWS S3,
 * MongoDB, and Milvus.
 * Provide valid document, collection, and team identifiers below before running
 * locally.
 * By default the test will be skipped to avoid accidental execution in CI.
 * </p>
 */
@SpringBootTest(classes = ApiGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Slf4j
class KnowledgeHubDocumentEmbeddingServiceImplIntegrationTest {

    /**
     * Team and document identifiers for the embedding test.
     * Replace the placeholders with real values before running.
     */
    private static final String TEAM_ID = "67d0aeb17172416c411d419e";
    private static final String DOCUMENT_ID = "d275933c-7a11-46d5-a2f8-d240d72f5b4d";
    private static final String MILVUS_COLLECTION_NAME = "uscis_marriage_based_files_openai_text_embedding_3_small_1536";
    private static final String S3_BUCKET_NAME = "linqra-knowledge-hub-dev";

    @Autowired
    private KnowledgeHubDocumentEmbeddingService embeddingService;

    @Autowired
    private LinqMilvusStoreService milvusStoreService;

    @Autowired
    private StorageProperties storageProperties;

    @MockitoBean
    private LinqraVaultService linqraVaultService;

    @MockitoBean
    private ChunkEncryptionService chunkEncryptionService;

    @MockitoBean
    private AuditLogHelper auditLogHelper;

    @MockitoBean
    private Driver neo4jDriver;

    private boolean shouldRun;

    @BeforeEach
    void setUp() {
        shouldRun = !(TEAM_ID.startsWith("YOUR_")
                || DOCUMENT_ID.startsWith("YOUR_")
                || MILVUS_COLLECTION_NAME.startsWith("YOUR_"));

        if (!shouldRun) {
            log.warn(
                    "⚠️  Skipping KnowledgeHubDocumentEmbeddingServiceImplIntegrationTest because test constants are not configured.");
            return;
        }

        // Configure mocks
        when(chunkEncryptionService.encryptChunkText(anyString(), anyString()))
                .thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(chunkEncryptionService.decryptChunkText(anyString(), anyString(), anyString()))
                .thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(chunkEncryptionService.getCurrentKeyVersion(anyString())).thenReturn(Mono.just("v1"));

        // Configure AuditLogHelper to return empty Mono for all method signatures
        lenient().when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), anyString(), anyString(), anyMap(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), anyString(), anyString(), anyMap(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        lenient().when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditLogHelper.logDetailedEvent(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        log.info("🔧 Configuring S3 bucket for integration test: {}", S3_BUCKET_NAME);
        storageProperties.setBucketName(S3_BUCKET_NAME);
    }

    @Test
    void testEmbedDocument_PersistsEmbeddingsToMilvus() {
        if (!shouldRun) {
            return;
        }

        log.info("📄 Running embedding integration test for document {}", DOCUMENT_ID);

        // Clean up any existing embeddings for a deterministic start.
        Long initialCount = milvusStoreService
                .countDocumentEmbeddings(MILVUS_COLLECTION_NAME, DOCUMENT_ID, TEAM_ID)
                .defaultIfEmpty(0L)
                .block();
        log.info("📊 Initial embedding count: {}", initialCount);

        // Execute the embedding workflow.
        StepVerifier.create(embeddingService.embedDocument(DOCUMENT_ID, TEAM_ID))
                .verifyComplete();

        Long finalCount = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            finalCount = milvusStoreService
                    .countDocumentEmbeddings(MILVUS_COLLECTION_NAME, DOCUMENT_ID, TEAM_ID)
                    .block();
            if (finalCount != null && finalCount > initialCount) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        assertNotNull(finalCount, "countDocumentEmbeddings should return a count");
        assertTrue(finalCount > initialCount, "Expected embeddings to be stored in Milvus");
        log.info("✅ Verified embeddings increased from {} to {} for document {}", initialCount, finalCount,
                DOCUMENT_ID);
    }
}
