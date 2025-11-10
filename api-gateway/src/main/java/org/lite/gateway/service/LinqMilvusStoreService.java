package org.lite.gateway.service;

import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.dto.MilvusCollectionSchemaInfo;
import org.lite.gateway.dto.MilvusCollectionVerificationResponse;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

public interface LinqMilvusStoreService {
    /**
     * Creates a new Milvus collection with the specified schema.
     *
     * @param collectionName The name of the collection.
     * @param schemaFields A list of field schemas (name, dataType, params).
     * @param description Optional description of the collection.
     * @param teamId The team ID for the collection.
     * @return A Mono indicating completion.
     */
    Mono<Map<String, String>> createCollection(String collectionName,
                                               List<Map<String, Object>> schemaFields,
                                               String description,
                                               String teamId,
                                               String collectionType,
                                               Map<String, String> properties);

    /**
     * Generates an embedding for a given text using the specified embedding llm.
     *
     * @param text The text to embed.
     * @param modelCategory Target of the embedding modelCategory (e.g., "openai-embed", "huggingface", "gemini-embed").
     * @param modelName The embedding model (e.g., "text-embedding-ada-002", "all-MiniLM-L6-v2").
     * @param teamId The team ID for the embedding request.
     * @return A Mono emitting the embedding as a List of Floats.
     */
    Mono<List<Float>> getEmbedding(String text, String modelCategory, String modelName, String teamId);

    /**
     * Stores a record in the specified Milvus collection.
     *
     * @param collectionName The name of the collection.
     * @param record A map of field names to values (e.g., id, text, embedding).
     * @param modelCategory The embedding modelCategory for generating embeddings (can be null if embedding is provided).
     * @param modelName The embedding model (can be null if embedding is provided).
     * @param textField The field name containing the text to embed.
     * @param teamId The team ID for the record.
     * @param embedding Optional pre-computed embedding from previous workflow step. If provided, skips embedding generation.
     * @return A Mono indicating completion.
     */
    Mono<Map<String, String>> storeRecord(String collectionName, Map<String, Object> record, String modelCategory, String modelName, String textField, String teamId, List<Float> embedding);

    /**
     * Queries the specified Milvus collection for similar records.
     *
     * @param collectionName The name of the collection.
     * @param embedding The query embedding.
     * @param nResults The number of results to return.
     * @param outputFields The fields to return in the results.
     * @param teamId The team ID for the query.
     * @return A Mono emitting a Map containing the query results (ids, documents, fields, distances).
     */
    Mono<Map<String, Object>> queryRecords(String collectionName, List<Float> embedding, int nResults, String[] outputFields, String teamId);

    /**
     * Deletes a Milvus collection.
     *
     * @param collectionName The name of the collection.
     * @param teamId The team ID for the collection.
     * @return A Mono indicating completion.
     */
    Mono<Map<String, String>> deleteCollection(String collectionName, String teamId);

    /**
     * Lists all available collections.
     *
     * @param teamId The team ID for the collections.
     * @return A Mono emitting a List of collection information.
     */
    Mono<List<MilvusCollectionInfo>> listCollections(String teamId, String collectionType);

    Mono<Map<String, String>> updateCollectionMetadata(String collectionName,
                                                       String teamId,
                                                       Map<String, String> metadata);

    /**
     * Lists all collections in the system. Only accessible by SUPER_ADMIN users.
     *
     * @return A Mono emitting a List of all collection information.
     */
    Mono<List<MilvusCollectionInfo>> listAllCollections();

    /**
     * Verify a record in the collection by searching for it using the text field
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param modelCategory The embedding modelCategory to use (e.g., "openai-embed")
     * @param modelName The embedding model to use (e.g., "text-embedding-3-small")
     * @return A Mono containing the verification results
     */
    Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String modelCategory, String modelName);

    /**
     * Verify a record in the collection by searching for it using the text field with metadata filtering
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param modelCategory The embedding modelCategory to use (e.g., "openai-embed", "gemini-embed")
     * @param modelName The embedding model to use (e.g., "text-embedding-3-small", "gemini-embedding-001")
     * @param metadataFilters Optional metadata filters to apply before vector search
     * @return A Mono containing the verification results with metadata
     */
    Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String modelCategory, String modelName, Map<String, Object> metadataFilters);

    /**
     * Search for multiple relevant records in the collection using semantic similarity
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param modelCategory The embedding modelCategory to use (e.g., "openai-embed")
     * @param modelName The embedding model to use (e.g., "text-embedding-3-small")
     * @param nResults The number of results to return (default: 10)
     * @param metadataFilters Optional metadata filters to apply before vector search
     * @return A Mono containing the search results with multiple records
     */
    Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String modelCategory, String modelName, int nResults, Map<String, Object> metadataFilters);

    /**
     * Search for multiple relevant records in the collection using semantic similarity (with default nResults=10)
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param modelCategory The embedding modelCategory to use (e.g., "openai-embed", "gemini-embed")
     * @param modelName The embedding model to use (e.g., "text-embedding-3-small", "gemini-embedding-001")
     * @return A Mono containing the search results with multiple records
     */
    Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String modelCategory, String modelName);

    /**
     * Lists all collections with detailed information including schema and statistics.
     *
     * @return A Mono emitting a map with detailed collection information.
     */
    Mono<Map<String, Object>> getCollectionDetails();

    /**
     * Delete all records for a document within a Milvus collection (scoped to team).
     *
      * @param collectionName Milvus collection name
      * @param documentId Document identifier whose embeddings should be removed
      * @param teamId Team identifier used for access control filtering
      * @return Mono emitting the number of deleted vectors
      */
    Mono<Long> deleteDocumentEmbeddings(String collectionName, String documentId, String teamId);

    /**
     * Counts the embeddings stored for a document in a Milvus collection (scoped to team).
     *
     * @param collectionName Milvus collection name
     * @param documentId Document identifier
     * @param teamId Team identifier used for access control filtering
     * @return Mono emitting the number of vectors currently stored
     */
    Mono<Long> countDocumentEmbeddings(String collectionName, String documentId, String teamId);

    /**
     * Describe and verify a Milvus collection for a team.
     *
     * @param collectionName Milvus collection name
     * @param teamId Team identifier
     * @return Verification result including schema details and validation issues
     */
    Mono<MilvusCollectionVerificationResponse> verifyCollection(String collectionName, String teamId);

    /**
     * Retrieve cached schema information for a Milvus collection.
     *
     * @param collectionName Milvus collection name
     * @return Schema info including field names and collection type
     */
    Mono<MilvusCollectionSchemaInfo> getCollectionSchema(String collectionName);
}
