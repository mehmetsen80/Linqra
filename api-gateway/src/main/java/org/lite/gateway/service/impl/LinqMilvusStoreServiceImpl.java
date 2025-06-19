package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.service.LinqToolService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.grpc.SearchResultData;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.R;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.collection.AlterCollectionParam;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqMilvusStoreServiceImpl implements LinqMilvusStoreService {

    private final LinqToolService linqToolService;
    private final MilvusServiceClient milvusClient;

    private static final String EMBEDDING_FIELD = "embedding";
    private static final IndexType INDEX_TYPE = IndexType.HNSW;
    private static final MetricType METRIC_TYPE = MetricType.COSINE;
    private static final int INDEX_PARAM_M = 8;
    private static final int INDEX_PARAM_EF_CONSTRUCTION = 64;
    private static final int SEARCH_PARAM_EF = 64;
    private static final int SHARDS_NUM = 2;

    private static final Map<String, DataType> DATA_TYPE_MAP = Map.ofEntries(
        Map.entry("BOOL", DataType.Bool),
        Map.entry("INT8", DataType.Int8),
        Map.entry("INT16", DataType.Int16),
        Map.entry("INT32", DataType.Int32),
        Map.entry("INT64", DataType.Int64),
        Map.entry("FLOAT", DataType.Float),
        Map.entry("DOUBLE", DataType.Double),
        Map.entry("STRING", DataType.String),
        Map.entry("VARCHAR", DataType.VarChar),
        Map.entry("ARRAY", DataType.Array),
        Map.entry("JSON", DataType.JSON),
        Map.entry("GEOMETRY", DataType.Geometry),
        Map.entry("BINARY_VECTOR", DataType.BinaryVector),
        Map.entry("FLOAT_VECTOR", DataType.FloatVector),
        Map.entry("FLOAT16_VECTOR", DataType.Float16Vector),
        Map.entry("BFLOAT16_VECTOR", DataType.BFloat16Vector),
        Map.entry("SPARSE_FLOAT_VECTOR", DataType.SparseFloatVector)
    );

    @Override
    public Mono<Map<String, String>> createCollection(String collectionName, List<Map<String, Object>> schemaFields, String description, String teamId) {
        log.info("Creating Milvus collection {} for team {}", collectionName, teamId);
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            
            if (hasCollection.getData()) {
                log.info("Collection {} already exists, skipping creation", collectionName);
                return Mono.just(Map.of("message", "Collection " + collectionName + " already exists, skipping creation"));
            }

            List<FieldType> fields = schemaFields.stream().map(this::mapToFieldType).toList();

            CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription(description != null ? description : "")
                    .withShardsNum(SHARDS_NUM)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withSchema(CollectionSchemaParam.newBuilder()
                            .withFieldTypes(fields)
                            .build());

            milvusClient.createCollection(builder.build());
            log.info("Created Milvus collection: {}", collectionName);

            // Create index on embedding field
            String embeddingField = schemaFields.stream()
                    .filter(f -> "FLOAT_VECTOR".equals(f.get("dtype")))
                    .findFirst()
                    .map(f -> (String) f.get("name"))
                    .orElseThrow(() -> new IllegalArgumentException("No FloatVector field found"));

            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(embeddingField)
                    .withIndexType(INDEX_TYPE)
                    .withMetricType(METRIC_TYPE)
                    .withExtraParam("{\"M\":" + INDEX_PARAM_M + ",\"efConstruction\":" + INDEX_PARAM_EF_CONSTRUCTION + "}")
                    .build();

            milvusClient.createIndex(indexParam);
            log.info("Created index on field: {}", embeddingField);

            // Load collection
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            log.info("Loaded Milvus collection: {}", collectionName);

            // Set teamId as a collection property
            AlterCollectionParam alterParam = AlterCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withProperty("teamId", teamId)
                .build();
            milvusClient.alterCollection(alterParam);
            log.info("Set teamId {} as collection property for {}", teamId, collectionName);

            return Mono.just(Map.of("message", "Collection " + collectionName + " created successfully"));
        } catch (Exception e) {
            log.error("Failed to create collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    private FieldType mapToFieldType(Map<String, Object> field) {
        String name = (String) field.get("name");
        String dtype = (String) field.get("dtype");
        Boolean isPrimary = (Boolean) field.get("is_primary");
        Integer maxLength = (Integer) field.get("max_length");
        Integer dim = (Integer) field.get("dim");

        DataType dataType = DATA_TYPE_MAP.get(dtype);
        if (dataType == null) {
            throw new IllegalArgumentException("Unsupported data type: " + dtype + 
                ". Supported types are: " + String.join(", ", DATA_TYPE_MAP.keySet()));
        }

        FieldType.Builder builder = FieldType.newBuilder()
                .withName(name)
                .withDataType(dataType);

        if (isPrimary != null && isPrimary) {
            builder.withPrimaryKey(true);
        }

        if (dataType == DataType.VarChar && maxLength != null) {
            builder.withMaxLength(maxLength);
        } else if (dataType == DataType.FloatVector && dim != null) {
            builder.withDimension(dim);
        }

        return builder.build();
    }

    @Override
    public Mono<Map<String, String>> storeRecord(String collectionName, Map<String, Object> record, String targetTool, String modelType, String textField, String teamId) {
        log.info("Storing record in collection {} for team {}", collectionName, teamId);
        try {
            String text = (String) record.get(textField);
            if (text == null) {
                return Mono.error(new IllegalArgumentException("Text field " + textField + " not found in record"));
            }

            if (teamId == null || teamId.trim().isEmpty()) {
                return Mono.error(new IllegalArgumentException("teamId cannot be null or empty"));
            }

            return getEmbedding(text, targetTool, modelType, teamId)
                    .flatMap(embedding -> {
                        try {
                            // First get the collection schema to ensure we provide all fields
                            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                                DescribeCollectionParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .build()
                            );
                            
                            List<InsertParam.Field> fields = new ArrayList<>();
                            
                            // Add ID field with timestamp-based unique ID
                            long uniqueId = System.currentTimeMillis();
                            fields.add(new InsertParam.Field("id", Collections.singletonList(uniqueId)));
                            
                            // Add embedding field
                            fields.add(new InsertParam.Field("embedding", Collections.singletonList(embedding)));
                            
                            // Add text field
                            fields.add(new InsertParam.Field(textField, Collections.singletonList(text)));
                            
                            // Add all other fields from the schema, using null if not provided in the record
                            for (io.milvus.grpc.FieldSchema fieldSchema : describeResponse.getData().getSchema().getFieldsList()) {
                                String fieldName = fieldSchema.getName();
                                if (!fieldName.equals("id") && !fieldName.equals("embedding") && !fieldName.equals(textField)) {
                                    Object value = record.get(fieldName);
                                    
                                    // Check if field is required (not nullable) and value is null
                                    if (value == null && !fieldSchema.getNullable()) {
                                        // For non-nullable fields, provide a default value based on the data type
                                        value = switch (fieldSchema.getDataType()) {
                                            case Int64, Int32 -> 0L;
                                            case Float -> 0.0f;
                                            case Double -> 0.0;
                                            case VarChar, String -> "";
                                            default ->
                                                    throw new IllegalArgumentException("Field '" + fieldName + "' is required but no value was provided and no default value is available for its type");
                                        };
                                    }
                                    
                                    // If value is null, pass it through as-is
                                    Object convertedValue = value;
                                    if (value != null) {
                                        try {
                                            // Convert values based on the field's data type from schema
                                            switch (fieldSchema.getDataType()) {
                                                case Int64:
                                                    convertedValue = ((Number) value).longValue();
                                                    break;
                                                case Int32:
                                                    convertedValue = ((Number) value).intValue();
                                                    break;
                                                case Float:
                                                    convertedValue = ((Number) value).floatValue();
                                                    break;
                                                case Double:
                                                    convertedValue = ((Number) value).doubleValue();
                                                    break;
                                                case VarChar:
                                                case String:
                                                    convertedValue = value.toString();
                                                    break;
                                                default:
                                            }
                                        } catch (Exception e) {
                                            log.warn("Failed to convert value for field {}: {}. Using original value.", fieldName, e.getMessage());
                                        }
                                    }
                                    
                                    // Add the field with either the converted value or null
                                    fields.add(new InsertParam.Field(fieldName, Collections.singletonList(convertedValue)));
                                }
                            }

                            InsertParam insertParam = InsertParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .withFields(fields)
                                    .build();
                            
                            log.info("Inserting record with fields: {}", fields.stream()
                                    .map(f -> f.getName() + ": " + f.getValues().getFirst())
                                    .collect(Collectors.joining(", ")));
                            
                            milvusClient.insert(insertParam);
                            log.info("Stored record in collection {}", collectionName);
                            return Mono.just(Map.of("message", "Record stored successfully in collection " + collectionName));
                        } catch (Exception e) {
                            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage(), e);
                            return Mono.error(e);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<List<Float>> getEmbedding(String text, String targetTool, String modelType, String teamId) {
        log.info("Getting embedding for text: {} with tool: {} and model: {} for team: {}", text, targetTool, modelType, teamId);
        LinqRequest request = new LinqRequest();
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(targetTool);
        link.setAction("generate");
        request.setLink(link);

        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent("embed");
        query.setParams(Map.of("text", text));
        
        LinqRequest.Query.ToolConfig toolConfig = new LinqRequest.Query.ToolConfig();
        toolConfig.setModel(modelType);
        query.setToolConfig(toolConfig);
        request.setQuery(query);

        return linqToolService.findByTargetAndTeam(targetTool, teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Embedding tool " + targetTool + " not found for team: " + teamId)))
                .flatMap(tool -> linqToolService.executeToolRequest(request, tool))
                .map(response -> {
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    if (result == null) {
                        throw new IllegalStateException("Received null result from embedding service");
                    }
                    
                    if (result.containsKey("error")) {
                        throw new IllegalStateException("Embedding service error: " + result.get("error"));
                    }

                    if ("openai-embed".equals(targetTool)) {
                        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                        if (data == null || data.isEmpty()) {
                            throw new IllegalStateException("No data received from OpenAI embedding service");
                        }
                        List<Object> rawEmbedding = (List<Object>) data.get(0).get("embedding");
                        return rawEmbedding.stream()
                            .map(value -> ((Number) value).floatValue())
                            .collect(Collectors.toList());
                    } else if ("huggingface".equals(targetTool)) {
                        List<Object> rawEmbedding = (List<Object>) result.get("embedding");
                        return rawEmbedding.stream()
                            .map(value -> ((Number) value).floatValue())
                            .collect(Collectors.toList());
                    } else if ("gemini-embed".equals(targetTool)) {
                        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                        if (data == null || data.isEmpty()) {
                            throw new IllegalStateException("No data received from Gemini embedding service");
                        }
                        List<Object> rawEmbedding = (List<Object>) data.get(0).get("embedding");
                        return rawEmbedding.stream()
                            .map(value -> ((Number) value).floatValue())
                            .collect(Collectors.toList());
                    }
                    throw new IllegalArgumentException("Unsupported embedding tool: " + targetTool);
                });
    }

    @Override
    public Mono<Map<String, Object>> queryRecords(String collectionName, List<Float> embedding, int nResults, String[] outputFields, String teamId) {
        log.info("Querying collection {} for {} results (team: {})", collectionName, nResults, teamId);
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFloatVectors(List.of(embedding))
                    .withTopK(nResults)
                    .withMetricType(METRIC_TYPE)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withVectorFieldName(EMBEDDING_FIELD)
                    .withOutFields(Arrays.asList(outputFields))
                    .withExpr("teamId == \"" + teamId + "\"")  // Filter by teamId during search
                    .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}")
                    .build();

            SearchResults results = milvusClient.search(searchParam).getData();
            List<Object> ids = new ArrayList<>();
            List<Object> documents = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            List<Float> distances = new ArrayList<>();

            String idField = Arrays.stream(outputFields).filter(f -> f.equals("id")).findFirst().orElse("id");
            String textField = Arrays.stream(outputFields).filter(f -> !f.equals("id") && !f.equals("embedding")).findFirst().orElse(outputFields[0]);

            SearchResultData resultData = results.getResults();
            
            // Extract results using the correct field access method
            for (int i = 0; i < resultData.getNumQueries(); i++) {
                // Get ID field
                var idFieldData = resultData.getFieldsDataList().stream()
                    .filter(f -> f.getFieldName().equals("id"))
                    .findFirst()
                    .orElse(null);
                if (idFieldData != null && idFieldData.getScalars().hasLongData() && idFieldData.getScalars().getLongData().getDataCount() > i) {
                    ids.add(idFieldData.getScalars().getLongData().getData(i));
                }
                
                // Get text field
                var textFieldData = resultData.getFieldsDataList().stream()
                    .filter(f -> f.getFieldName().equals(textField))
                    .findFirst()
                    .orElse(null);
                if (textFieldData != null && textFieldData.getScalars().hasStringData() && textFieldData.getScalars().getStringData().getDataCount() > i) {
                    documents.add(textFieldData.getScalars().getStringData().getData(i));
                }
                
                // Get distance field
                var distanceField = resultData.getFieldsDataList().stream()
                    .filter(f -> f.getFieldName().equals("distance"))
                    .findFirst()
                    .orElse(null);
                
                if (distanceField != null) {
                    log.info("Found distance field: {}", distanceField.getScalars().getDataCase());
                    if (distanceField.getScalars().hasFloatData() && distanceField.getScalars().getFloatData().getDataCount() > i) {
                        distances.add(distanceField.getScalars().getFloatData().getData(i));
                    } else if (distanceField.getScalars().hasDoubleData() && distanceField.getScalars().getDoubleData().getDataCount() > i) {
                        distances.add((float) distanceField.getScalars().getDoubleData().getData(i));
                    } else {
                        log.warn("Distance field found but no data available at index {}", i);
                        distances.add(0.0f); // Default distance
                    }
                } else {
                    log.warn("Distance field not found in search results");
                    distances.add(0.0f); // Default distance
                }
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("ids", ids);
            resultMap.put("documents", documents);
            resultMap.put("metadatas", metadatas);
            resultMap.put("distances", distances);

            log.info("Extracted search results - ids: {}, documents: {}, distances: {}", 
                ids.size(), documents.size(), distances.size());
            
            // Log available fields for debugging
            log.info("Available fields in search results:");
            for (var field : resultData.getFieldsDataList()) {
                log.info("Field: {} - Type: {} - Data count: {}", 
                    field.getFieldName(), 
                    field.getScalars().getDataCase(),
                    field.getScalars().hasLongData() ? field.getScalars().getLongData().getDataCount() :
                    field.getScalars().hasStringData() ? field.getScalars().getStringData().getDataCount() :
                    field.getScalars().hasFloatData() ? field.getScalars().getFloatData().getDataCount() :
                    field.getScalars().hasDoubleData() ? field.getScalars().getDoubleData().getDataCount() : 0);
            }

            log.info("Retrieved {} results from collection {} for team {}", ids.size(), collectionName, teamId);
            return Mono.just(resultMap);
        } catch (Exception e) {
            log.error("Failed to query collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Map<String, String>> deleteCollection(String collectionName, String teamId) {
        log.info("Deleting collection {} for team {}", collectionName, teamId);
        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            
            if (!hasCollection.getData()) {
                log.info("Collection {} does not exist, skipping deletion", collectionName);
                return Mono.just(Map.of("message", "Collection " + collectionName + " does not exist"));
            }

            milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            log.info("Deleted collection {}", collectionName);

            return Mono.just(Map.of("message", "Collection " + collectionName + " deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<List<MilvusCollectionInfo>> listCollections(String teamId) {
        log.info("Listing collections for team {}", teamId);
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder().build());
            List<String> allCollections = response.getData().getCollectionNamesList();
            
            // Filter collections in parallel using CompletableFuture
            List<CompletableFuture<MilvusCollectionInfo>> futures = allCollections.stream()
                .map(collectionName -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Get collection properties
                        R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                            DescribeCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build()
                        );
                        
                        // Check if collection has teamId property matching the requested teamId
                        for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                            if ("teamId".equals(property.getKey()) && teamId.equals(property.getValue())) {
                                log.info("Found collection {} for team {}", collectionName, teamId);
                                return new MilvusCollectionInfo(collectionName, teamId);
                            }
                        }
                        return null;
                    } catch (Exception e) {
                        log.warn("Failed to get properties for collection {}: {}", collectionName, e.getMessage());
                        return null;
                    }
                }))
                .toList();

            // Wait for all checks to complete and collect results
            List<MilvusCollectionInfo> collections = new ArrayList<>();
            for (CompletableFuture<MilvusCollectionInfo> future : futures) {
                MilvusCollectionInfo info = future.get();
                if (info != null) {
                    collections.add(info);
                }
            }

            log.info("Found {} collections for team {}", collections.size(), teamId);
            return Mono.just(collections);
        } catch (Exception e) {
            log.error("Failed to list collections: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<List<MilvusCollectionInfo>> listAllCollections() {
        log.info("Listing all collections");
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder().build());
            List<String> allCollections = response.getData().getCollectionNamesList();
            
            // Filter collections in parallel using CompletableFuture
            List<CompletableFuture<MilvusCollectionInfo>> futures = allCollections.stream()
                .map(collectionName -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Get collection properties
                        R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                            DescribeCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .build()
                        );
                        
                        // Find teamId in properties
                        for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                            if ("teamId".equals(property.getKey())) {
                                String teamId = property.getValue();
                                log.info("Found teamId {} for collection {}", teamId, collectionName);
                                return new MilvusCollectionInfo(collectionName, teamId);
                            }
                        }
                        
                        log.info("No teamId found for collection {}", collectionName);
                        return new MilvusCollectionInfo(collectionName, "unknown");
                    } catch (Exception e) {
                        log.warn("Failed to get properties for collection {}: {}", collectionName, e.getMessage());
                        return new MilvusCollectionInfo(collectionName, "unknown");
                    }
                }))
                .toList();

            // Wait for all checks to complete and collect results
            List<MilvusCollectionInfo> collections = new ArrayList<>();
            for (CompletableFuture<MilvusCollectionInfo> future : futures) {
                MilvusCollectionInfo info = future.get();
                if (info != null) {
                    collections.add(info);
                }
            }

            log.info("Found {} collections", collections.size());
            return Mono.just(collections);
        } catch (Exception e) {
            log.error("Failed to list all collections: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType) {
        log.info("Verifying record in collection {} for team {} with text: {} using tool: {} and model: {}", collectionName, teamId, text, targetTool, modelType);
        try {
            // First verify the collection belongs to the team
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build()
            );
            
            boolean teamIdMatches = false;
            for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                if ("teamId".equals(property.getKey()) && teamId.equals(property.getValue())) {
                    teamIdMatches = true;
                    break;
                }
            }
            
            if (!teamIdMatches) {
                return Mono.just(Map.of("message", "Collection does not belong to team " + teamId));
            }

            // Get embedding for the search text using dynamic tool and model
            return getEmbedding(text, targetTool, modelType, teamId)
                .flatMap(searchEmbedding -> {
                    try {
                        // Use vector search to find similar records
                        SearchParam searchParam = SearchParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFloatVectors(List.of(searchEmbedding))
                            .withTopK(5)
                            .withMetricType(METRIC_TYPE)
                            .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                            .withVectorFieldName(EMBEDDING_FIELD)
                            .withOutFields(Arrays.asList("id", textField))
                            .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}")
                            .build();

                        log.info("Executing semantic search for text: {}", text);
                        SearchResults results = milvusClient.search(searchParam).getData();
                        
                        if (results == null || results.getResults().getNumQueries() == 0) {
                            log.info("No results found in semantic search");
                            return Mono.just(Map.of("message", "No similar records found"));
                        }

                        SearchResultData resultData = results.getResults();
                        List<Object> ids = new ArrayList<>();
                        List<Object> documents = new ArrayList<>();
                        List<Float> distances = new ArrayList<>();

                        // Extract results using the correct field access method
                        for (int i = 0; i < resultData.getNumQueries(); i++) {
                            // Get ID field
                            var idFieldData = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals("id"))
                                .findFirst()
                                .orElse(null);
                            if (idFieldData != null && idFieldData.getScalars().hasLongData() && idFieldData.getScalars().getLongData().getDataCount() > i) {
                                ids.add(idFieldData.getScalars().getLongData().getData(i));
                            }
                            
                            // Get text field
                            var textFieldData = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals(textField))
                                .findFirst()
                                .orElse(null);
                            if (textFieldData != null && textFieldData.getScalars().hasStringData() && textFieldData.getScalars().getStringData().getDataCount() > i) {
                                documents.add(textFieldData.getScalars().getStringData().getData(i));
                            }
                            
                            // Get distance field
                            var distanceField = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals("distance"))
                                .findFirst()
                                .orElse(null);
                            
                            if (distanceField != null) {
                                log.info("Found distance field: {}", distanceField.getScalars().getDataCase());
                                if (distanceField.getScalars().hasFloatData() && distanceField.getScalars().getFloatData().getDataCount() > i) {
                                    distances.add(distanceField.getScalars().getFloatData().getData(i));
                                } else if (distanceField.getScalars().hasDoubleData() && distanceField.getScalars().getDoubleData().getDataCount() > i) {
                                    distances.add((float) distanceField.getScalars().getDoubleData().getData(i));
                                } else {
                                    log.warn("Distance field found but no data available at index {}", i);
                                    distances.add(0.0f); // Default distance
                                }
                            } else {
                                log.warn("Distance field not found in search results");
                                distances.add(0.0f); // Default distance
                            }
                        }

                        // Find exact text match among semantic search results
                        Map<String, Object> verification = new HashMap<>();
                        boolean foundExactMatch = false;
                        
                        // Check if we have any results before processing
                        if (ids.isEmpty() || documents.isEmpty() || distances.isEmpty()) {
                            log.warn("No search results found - ids: {}, documents: {}, distances: {}", 
                                ids.size(), documents.size(), distances.size());
                            return Mono.just(Map.of("message", "No search results found"));
                        }
                        
                        for (int i = 0; i < documents.size(); i++) {
                            String storedText = documents.get(i).toString();
                            if (storedText.equalsIgnoreCase(text)) {
                                verification.put("id", ids.get(i));
                                verification.put(textField, storedText);
                                verification.put("distance", distances.get(i));
                                verification.put("match_type", "exact");
                                foundExactMatch = true;
                                log.info("Found exact match with distance: {}", distances.get(i));
                                break;
                            }
                        }

                        if (!foundExactMatch) {
                            // Return the most similar result
                            verification.put("id", ids.getFirst());
                            verification.put(textField, documents.getFirst());
                            verification.put("distance", distances.getFirst());
                            verification.put("match_type", "semantic");
                            verification.put("search_text", text);
                            log.info("Found semantic match with distance: {}", distances.getFirst());
                        }

                        log.info("Successfully verified record with fields: {}", verification.keySet());
                        return Mono.just(verification);
                        
                    } catch (Exception e) {
                        log.error("Failed to perform semantic search: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                });
                
        } catch (Exception e) {
            log.error("Failed to verify record in collection {}: {}", collectionName, e.getMessage(), e);
            return Mono.error(e);
        }
    }
}
