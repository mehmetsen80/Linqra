package org.lite.gateway.service;

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
     * @param teamId The ID of the team.
     * @return A Mono indicating completion.
     */
    Mono<Void> createCollection(String collectionName, List<Map<String, Object>> schemaFields, String description, String teamId);

    /**
     * Generates an embedding for a given text using the specified embedding tool.
     *
     * @param text The text to embed.
     * @param targetTool Target of the embedding tool (e.g., "openai-embed", "huggingface", "gemini-embed").
     * @param modelType The embedding model (e.g., "text-embedding-ada-002", "all-MiniLM-L6-v2").
     * @param teamId The ID of the team.
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
     * @param teamId The ID of the team.
     * @return A Mono indicating completion.
     */
    Mono<Void> storeRecord(String collectionName, Map<String, Object> record, String targetTool, String modelType, String textField, String teamId);

    /**
     * Queries the specified Milvus collection for similar records.
     *
     * @param collectionName The name of the collection.
     * @param embedding The query embedding.
     * @param nResults The number of results to return.
     * @param outputFields The fields to return in the results.
     * @param teamId The ID of the team.
     * @return A Mono emitting a Map containing the query results (ids, documents, fields, distances).
     */
    Mono<Map<String, Object>> queryRecords(String collectionName, List<Float> embedding, int nResults, String[] outputFields, String teamId);

    /**
     * Populates a Milvus collection with data from a source service.
     *
     * @param collectionName The name of the collection.
     * @param sourceService The source service target (e.g., "quotes-service").
     * @param intent The intent for the source service request.
     * @param action The action to perform on the source service.
     * @param textField The field to embed.
     * @param teamId The ID of the team.
     * @return A Mono indicating completion.
     */
    Mono<Void> populateCollection(String collectionName, String sourceService, String intent, String action, String textField, String teamId);
}
