package org.lite.gateway.service;

import org.lite.gateway.dto.MilvusCollectionInfo;
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
    Mono<Map<String, String>> createCollection(String collectionName, List<Map<String, Object>> schemaFields, String description, String teamId);

    /**
     * Generates an embedding for a given text using the specified embedding tool.
     *
     * @param text The text to embed.
     * @param targetTool Target of the embedding tool (e.g., "openai-embed", "huggingface", "gemini-embed").
     * @param modelType The embedding model (e.g., "text-embedding-ada-002", "all-MiniLM-L6-v2").
     * @param teamId The team ID for the embedding request.
     * @return A Mono emitting the embedding as a List of Floats.
     */
    Mono<List<Float>> getEmbedding(String text, String targetTool, String modelType, String teamId);

    /**
     * Stores a record in the specified Milvus collection.
     *
     * @param collectionName The name of the collection.
     * @param record A map of field names to values (e.g., id, text, embedding).
     * @param targetTool The embedding tool for generating embeddings.
     * @param modelType The embedding model.
     * @param textField The field name containing the text to embed.
     * @param teamId The team ID for the record.
     * @return A Mono indicating completion.
     */
    Mono<Map<String, String>> storeRecord(String collectionName, Map<String, Object> record, String targetTool, String modelType, String textField, String teamId);

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
    Mono<List<MilvusCollectionInfo>> listCollections(String teamId);

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
     * @param targetTool The embedding tool to use (e.g., "openai-embed")
     * @param modelType The embedding model to use (e.g., "text-embedding-3-small")
     * @return A Mono containing the verification results
     */
    Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType);

    /**
     * Verify a record in the collection by searching for it using the text field with metadata filtering
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param targetTool The embedding tool to use (e.g., "openai-embed")
     * @param modelType The embedding model to use (e.g., "text-embedding-3-small")
     * @param metadataFilters Optional metadata filters to apply before vector search
     * @return A Mono containing the verification results with metadata
     */
    Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType, Map<String, Object> metadataFilters);

    /**
     * Search for multiple relevant records in the collection using semantic similarity
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param targetTool The embedding tool to use (e.g., "openai-embed")
     * @param modelType The embedding model to use (e.g., "text-embedding-3-small")
     * @param nResults The number of results to return (default: 10)
     * @param metadataFilters Optional metadata filters to apply before vector search
     * @return A Mono containing the search results with multiple records
     */
    Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType, int nResults, Map<String, Object> metadataFilters);

    /**
     * Search for multiple relevant records in the collection using semantic similarity (with default nResults=10)
     * @param collectionName The name of the collection
     * @param textField The name of the text field to search
     * @param text The text to search for
     * @param teamId The team ID
     * @param targetTool The embedding tool to use (e.g., "openai-embed")
     * @param modelType The embedding model to use (e.g., "text-embedding-3-small")
     * @return A Mono containing the search results with multiple records
     */
    Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType);

    /**
     * Lists all collections with detailed information including schema and statistics.
     *
     * @return A Mono emitting a map with detailed collection information.
     */
    Mono<Map<String, Object>> getCollectionDetails();
}
