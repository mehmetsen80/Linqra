package service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lite.gateway.ApiGatewayApplication;
import org.lite.gateway.config.KnowledgeHubS3Properties;
import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final String DOCUMENT_ID = "d287c97e-eb9e-4c7e-bc05-59e7e9b7b169";
    private static final String MILVUS_COLLECTION_NAME = "uscis_marriage_based_files_openai_text_embedding_3_small_1536";
    private static final String S3_BUCKET_NAME = "linqra-knowledge-hub-dev";

    @Autowired
    private KnowledgeHubDocumentEmbeddingService embeddingService;

    @Autowired
    private LinqMilvusStoreService milvusStoreService;

    @Autowired
    private KnowledgeHubS3Properties s3Properties;

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

        log.info("🔧 Configuring S3 bucket for integration test: {}", S3_BUCKET_NAME);
        s3Properties.setBucketName(S3_BUCKET_NAME);
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
