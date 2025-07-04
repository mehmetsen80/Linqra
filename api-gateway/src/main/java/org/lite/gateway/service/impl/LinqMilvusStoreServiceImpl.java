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
import io.milvus.grpc.FieldData;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.grpc.GetCollectionStatisticsResponse;

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
                    .withDatabaseName("default")
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
                    .withDatabaseName("default")
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
                    .withDatabaseName("default")
                    .withExtraParam("{\"M\":" + INDEX_PARAM_M + ",\"efConstruction\":" + INDEX_PARAM_EF_CONSTRUCTION + "}")
                    .build();

            milvusClient.createIndex(indexParam);
            log.info("Created index on field: {}", embeddingField);

            // Load collection
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDatabaseName("default")
                    .build());
            log.info("Loaded Milvus collection: {}", collectionName);

            // Set teamId as a collection property
            AlterCollectionParam alterParam = AlterCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDatabaseName("default")
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
                                    .withDatabaseName("default")
                                    .build()
                            );
                            
                            List<InsertParam.Field> fields = new ArrayList<>();
                            
                            // Add ID field with timestamp-based unique ID
                            long uniqueId = System.currentTimeMillis();
                            fields.add(new InsertParam.Field("id", Collections.singletonList(uniqueId)));
                            
                            // Add created_at field with the same timestamp
                            fields.add(new InsertParam.Field("created_at", Collections.singletonList(uniqueId)));
                            
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
                                                    if (value instanceof String) {
                                                        String strValue = ((String) value).trim();
                                                        if (strValue.isEmpty()) {
                                                            convertedValue = 0L; // Default value for empty string
                                                        } else {
                                                            convertedValue = Long.parseLong(strValue);
                                                        }
                                                    } else if (value instanceof Number) {
                                                        convertedValue = ((Number) value).longValue();
                                                    } else {
                                                        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Long for field " + fieldName);
                                                    }
                                                    break;
                                                case Int32:
                                                    if (value instanceof String) {
                                                        String strValue = ((String) value).trim();
                                                        if (strValue.isEmpty()) {
                                                            convertedValue = 0; // Default value for empty string
                                                        } else {
                                                            convertedValue = Integer.parseInt(strValue);
                                                        }
                                                    } else if (value instanceof Number) {
                                                        convertedValue = ((Number) value).intValue();
                                                    } else {
                                                        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Integer for field " + fieldName);
                                                    }
                                                    break;
                                                case Float:
                                                    if (value instanceof String) {
                                                        String strValue = ((String) value).trim();
                                                        if (strValue.isEmpty()) {
                                                            convertedValue = 0.0f; // Default value for empty string
                                                        } else {
                                                            convertedValue = Float.parseFloat(strValue);
                                                        }
                                                    } else if (value instanceof Number) {
                                                        convertedValue = ((Number) value).floatValue();
                                                    } else {
                                                        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Float for field " + fieldName);
                                                    }
                                                    break;
                                                case Double:
                                                    if (value instanceof String) {
                                                        String strValue = ((String) value).trim();
                                                        if (strValue.isEmpty()) {
                                                            convertedValue = 0.0; // Default value for empty string
                                                        } else {
                                                            convertedValue = Double.parseDouble(strValue);
                                                        }
                                                    } else if (value instanceof Number) {
                                                        convertedValue = ((Number) value).doubleValue();
                                                    } else {
                                                        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Double for field " + fieldName);
                                                    }
                                                    break;
                                                case VarChar:
                                                case String:
                                                    convertedValue = value.toString();
                                                    break;
                                                default:
                                            }
                                        } catch (NumberFormatException e) {
                                            log.warn("Failed to parse number for field {} with value '{}': {}. Using default value.", fieldName, value, e.getMessage());
                                            // Use default values for number fields when parsing fails
                                            switch (fieldSchema.getDataType()) {
                                                case Int64 -> convertedValue = 0L;
                                                case Int32 -> convertedValue = 0;
                                                case Float -> convertedValue = 0.0f;
                                                case Double -> convertedValue = 0.0;
                                                default -> convertedValue = value; // Keep original for non-numeric fields
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
                        List<Object> rawEmbedding = (List<Object>) data.getFirst().get("embedding");
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
                        List<Object> rawEmbedding = (List<Object>) data.getFirst().get("embedding");
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
                    .withDatabaseName("default")
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
                    .withDatabaseName("default")
                    .build());
            
            if (!hasCollection.getData()) {
                log.info("Collection {} does not exist, skipping deletion", collectionName);
                return Mono.just(Map.of("message", "Collection " + collectionName + " does not exist"));
            }

            milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDatabaseName("default")
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
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder()
                    .withDatabaseName("default")
                    .build());
            List<String> allCollections = response.getData().getCollectionNamesList();
            
            // Filter collections in parallel using CompletableFuture
            List<CompletableFuture<MilvusCollectionInfo>> futures = allCollections.stream()
                .map(collectionName -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Get collection properties
                        R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                            DescribeCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withDatabaseName("default")
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
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder()
                    .withDatabaseName("default")
                    .build());
            List<String> allCollections = response.getData().getCollectionNamesList();
            
            // Filter collections in parallel using CompletableFuture
            List<CompletableFuture<MilvusCollectionInfo>> futures = allCollections.stream()
                .map(collectionName -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Get collection properties
                        R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                            DescribeCollectionParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withDatabaseName("default")
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
        return verifyRecord(collectionName, textField, text, teamId, targetTool, modelType, null);
    }

    @Override
    public Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType, Map<String, Object> metadataFilters) {
        log.info("Verifying record in collection {} for team {} with text: {} using tool: {} and model: {} with filters: {}", 
            collectionName, teamId, text, targetTool, modelType, metadataFilters);
        try {
            // First verify the collection belongs to the team
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDatabaseName("default")
                    .build()
            );
            
            // Log collection schema for debugging
            log.info("Collection schema for {}: {}", collectionName, describeResponse.getData().getSchema());
            log.info("Available fields:");
            for (io.milvus.grpc.FieldSchema field : describeResponse.getData().getSchema().getFieldsList()) {
                log.info("  - {}: {} (primary: {})", field.getName(), field.getDataType(), field.getIsPrimaryKey());
            }
            
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
                        // Build dynamic filter expression
                        String filterExpression = buildFilterExpression(teamId, metadataFilters, textField);
                        
                        // Build dynamic out fields
                        List<String> outFields = buildOutFields(textField, metadataFilters);
                        
                        // Use vector search to find similar records
                        SearchParam.Builder searchParamBuilder = SearchParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFloatVectors(List.of(searchEmbedding))
                            .withTopK(5)
                            .withMetricType(METRIC_TYPE)
                            .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                            .withVectorFieldName(EMBEDDING_FIELD)
                            .withOutFields(outFields)
                            .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}")
                            .withDatabaseName("default");
                        
                        // Only add filter expression if it's not empty
                        if (!filterExpression.isEmpty()) {
                            searchParamBuilder.withExpr(filterExpression);
                        }
                        
                        SearchParam searchParam = searchParamBuilder.build();

                        log.info("Executing semantic search for text: {} with filter: {}", text, filterExpression);
                        SearchResults results = milvusClient.search(searchParam).getData();
                        
                        if (results == null || results.getResults().getNumQueries() == 0) {
                            log.info("No results found in semantic search");
                            // Return consistent structure with null/empty values for missing data
                            Map<String, Object> noResultsResponse = new HashMap<>();
                            noResultsResponse.put("found", false);
                            noResultsResponse.put("message", "No search results found");
                            noResultsResponse.put("id", null);
                            noResultsResponse.put(textField, null);
                            noResultsResponse.put("distance", null);
                            noResultsResponse.put("match_type", null);
                            noResultsResponse.put("search_text", text);
                            // Add empty metadata fields if any were requested
                            if (metadataFilters != null) {
                                for (String fieldName : metadataFilters.keySet()) {
                                    noResultsResponse.put(fieldName, null);
                                }
                            }
                            return Mono.just(noResultsResponse);
                        }

                        SearchResultData resultData = results.getResults();
                        List<Object> ids = new ArrayList<>();
                        List<Object> documents = new ArrayList<>();
                        List<Float> distances = new ArrayList<>();
                        List<Map<String, Object>> metadataList = new ArrayList<>();

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
                            
                            // Extract metadata fields
                            Map<String, Object> metadata = new HashMap<>();
                            for (String fieldName : outFields) {
                                if (!fieldName.equals("id") && !fieldName.equals(textField)) {
                                    var fieldData = resultData.getFieldsDataList().stream()
                                        .filter(f -> f.getFieldName().equals(fieldName))
                                        .findFirst()
                                        .orElse(null);
                                    if (fieldData != null) {
                                        Object value = extractFieldValue(fieldData, i);
                                        metadata.put(fieldName, value);
                                    }
                                }
                            }
                            metadataList.add(metadata);
                        }

                        // Find exact text match among semantic search results
                        Map<String, Object> verification = new HashMap<>();
                        boolean foundExactMatch = false;
                        
                        // Check if we have any results before processing
                        if (ids.isEmpty() || documents.isEmpty() || distances.isEmpty()) {
                            log.warn("No search results found - ids: {}, documents: {}, distances: {}", 
                                ids.size(), documents.size(), distances.size());
                            // Return consistent structure with null/empty values for missing data
                            Map<String, Object> noResultsResponse = new HashMap<>();
                            noResultsResponse.put("found", false);
                            noResultsResponse.put("message", "No search results found");
                            noResultsResponse.put("id", null);
                            noResultsResponse.put(textField, null);
                            noResultsResponse.put("distance", null);
                            noResultsResponse.put("match_type", null);
                            noResultsResponse.put("search_text", text);
                            // Add empty metadata fields if any were requested
                            if (metadataFilters != null) {
                                for (String fieldName : metadataFilters.keySet()) {
                                    noResultsResponse.put(fieldName, null);
                                }
                            }
                            return Mono.just(noResultsResponse);
                        }
                        
                        for (int i = 0; i < documents.size(); i++) {
                            String storedText = documents.get(i).toString();
                            if (storedText.equalsIgnoreCase(text)) {
                                verification.put("found", true);
                                verification.put("id", ids.get(i));
                                verification.put(textField, storedText);
                                verification.put("distance", distances.get(i));
                                verification.put("match_type", "exact");
                                verification.put("search_text", text);
                                // Add metadata to response
                                if (i < metadataList.size()) {
                                    verification.putAll(metadataList.get(i));
                                }
                                foundExactMatch = true;
                                log.info("Found exact match with distance: {}", distances.get(i));
                                break;
                            }
                        }

                        if (!foundExactMatch) {
                            // Return the most similar result
                            verification.put("found", true);
                            verification.put("id", ids.getFirst());
                            verification.put(textField, documents.getFirst());
                            verification.put("distance", distances.getFirst());
                            verification.put("match_type", "semantic");
                            verification.put("search_text", text);
                            // Add metadata to response
                            if (!metadataList.isEmpty()) {
                                verification.putAll(metadataList.getFirst());
                            }
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

    /**
     * Build dynamic filter expression for metadata filtering
     */
    private String buildFilterExpression(String teamId, Map<String, Object> metadataFilters) {
        List<String> conditions = new ArrayList<>();
        
        // Note: Team filtering is optional since we already verify collection ownership
        // If you need team filtering, uncomment the line below and ensure 'teamId' field exists in your schema
        // conditions.add("teamId == \"" + teamId + "\"");
        
        // Add dynamic metadata filters
        if (metadataFilters != null) {
            for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
                String fieldName = filter.getKey();
                Object value = filter.getValue();
                
                if (value != null) {
                    if (value instanceof String) {
                        conditions.add(fieldName + " == \"" + value + "\"");
                    } else if (value instanceof Number) {
                        conditions.add(fieldName + " == " + value);
                    } else if (value instanceof Boolean) {
                        conditions.add(fieldName + " == " + value);
                    }
                }
            }
        }
        
        return conditions.isEmpty() ? "" : String.join(" && ", conditions);
    }

    /**
     * Build dynamic filter expression for metadata filtering with text field validation
     */
    private String buildFilterExpression(String teamId, Map<String, Object> metadataFilters, String textField) {
        List<String> conditions = new ArrayList<>();
        
        // Add filter to exclude empty or null text fields
        conditions.add(textField + " != \"\"");
        conditions.add(textField + " != null");
        
        // Note: Team filtering is optional since we already verify collection ownership
        // If you need team filtering, uncomment the line below and ensure 'teamId' field exists in your schema
        // conditions.add("teamId == \"" + teamId + "\"");
        
        // Add dynamic metadata filters
        if (metadataFilters != null) {
            for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
                String fieldName = filter.getKey();
                Object value = filter.getValue();
                
                if (value != null) {
                    if (value instanceof String) {
                        conditions.add(fieldName + " == \"" + value + "\"");
                    } else if (value instanceof Number) {
                        conditions.add(fieldName + " == " + value);
                    } else if (value instanceof Boolean) {
                        conditions.add(fieldName + " == " + value);
                    }
                }
            }
        }
        
        return String.join(" && ", conditions);
    }

    /**
     * Build dynamic out fields list
     */
    private List<String> buildOutFields(String textField, Map<String, Object> metadataFilters) {
        List<String> outFields = new ArrayList<>();
        outFields.add("id");
        outFields.add(textField);
        
        // Add metadata fields to the output
        if (metadataFilters != null) {
            outFields.addAll(metadataFilters.keySet());
        }
        
        return outFields;
    }

    /**
     * Extract field value based on data type
     */
    private Object extractFieldValue(FieldData fieldData, int index) {
        if (fieldData.getScalars().hasStringData() && fieldData.getScalars().getStringData().getDataCount() > index) {
            return fieldData.getScalars().getStringData().getData(index);
        } else if (fieldData.getScalars().hasLongData() && fieldData.getScalars().getLongData().getDataCount() > index) {
            return fieldData.getScalars().getLongData().getData(index);
        } else if (fieldData.getScalars().hasFloatData() && fieldData.getScalars().getFloatData().getDataCount() > index) {
            return fieldData.getScalars().getFloatData().getData(index);
        } else if (fieldData.getScalars().hasDoubleData() && fieldData.getScalars().getDoubleData().getDataCount() > index) {
            return fieldData.getScalars().getDoubleData().getData(index);
        } else if (fieldData.getScalars().hasBoolData() && fieldData.getScalars().getBoolData().getDataCount() > index) {
            return fieldData.getScalars().getBoolData().getData(index);
        }
        return null;
    }

    @Override
    public Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType) {
        return searchRecord(collectionName, textField, text, teamId, targetTool, modelType, 10, null);
    }

    @Override
    public Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId, String targetTool, String modelType, int nResults, Map<String, Object> metadataFilters) {
        log.info("Searching records in collection {} for team {} with text: {} using tool: {} and model: {} with filters: {} and nResults: {}", 
            collectionName, teamId, text, targetTool, modelType, metadataFilters, nResults);
        try {
            // First verify the collection belongs to the team
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDatabaseName("default")
                    .build()
            );
            
            // Log collection schema for debugging
            log.info("Collection schema for {}: {}", collectionName, describeResponse.getData().getSchema());
            log.info("Available fields:");
            for (io.milvus.grpc.FieldSchema field : describeResponse.getData().getSchema().getFieldsList()) {
                log.info("  - {}: {} (primary: {})", field.getName(), field.getDataType(), field.getIsPrimaryKey());
            }
            
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
                        // Build dynamic filter expression
                        String filterExpression = buildFilterExpression(teamId, metadataFilters);
                        
                        // Build dynamic out fields
                        List<String> outFields = buildOutFields(textField, metadataFilters);
                        
                        // Use vector search to find similar records
                        SearchParam.Builder searchParamBuilder = SearchParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withFloatVectors(List.of(searchEmbedding))
                            .withTopK(nResults)
                            .withMetricType(METRIC_TYPE)
                            .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                            .withVectorFieldName(EMBEDDING_FIELD)
                            .withOutFields(outFields)
                            .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}")
                            .withDatabaseName("default");
                        
                        // Only add filter expression if it's not empty
                        if (!filterExpression.isEmpty()) {
                            searchParamBuilder.withExpr(filterExpression);
                        }
                        
                        SearchParam searchParam = searchParamBuilder.build();

                        log.info("Executing semantic search for text: {} with filter: {} and nResults: {}", 
                            text, filterExpression.isEmpty() ? "none" : filterExpression, nResults);
                        SearchResults results = milvusClient.search(searchParam).getData();
                        
                        if (results == null || results.getResults().getNumQueries() == 0) {
                            log.info("No results found in semantic search");
                            // Return consistent structure for no results
                            Map<String, Object> noResultsResponse = new HashMap<>();
                            noResultsResponse.put("found", false);
                            noResultsResponse.put("message", "No search results found");
                            noResultsResponse.put("search_text", text);
                            noResultsResponse.put("total_results", 0);
                            noResultsResponse.put("results", new ArrayList<>());
                            return Mono.just(noResultsResponse);
                        }

                        SearchResultData resultData = results.getResults();
                        List<Map<String, Object>> searchResults = new ArrayList<>();

                        // Extract results using the correct field access method
                        for (int i = 0; i < resultData.getNumQueries(); i++) {
                            Map<String, Object> record = new HashMap<>();
                            
                            // Get ID field
                            var idFieldData = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals("id"))
                                .findFirst()
                                .orElse(null);
                            if (idFieldData != null && idFieldData.getScalars().hasLongData() && idFieldData.getScalars().getLongData().getDataCount() > i) {
                                record.put("id", idFieldData.getScalars().getLongData().getData(i));
                            }
                            
                            // Get text field
                            var textFieldData = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals(textField))
                                .findFirst()
                                .orElse(null);
                            if (textFieldData != null && textFieldData.getScalars().hasStringData() && textFieldData.getScalars().getStringData().getDataCount() > i) {
                                record.put(textField, textFieldData.getScalars().getStringData().getData(i));
                            }
                            
                            // Get distance field
                            var distanceField = resultData.getFieldsDataList().stream()
                                .filter(f -> f.getFieldName().equals("distance"))
                                .findFirst()
                                .orElse(null);
                            
                            if (distanceField != null) {
                                if (distanceField.getScalars().hasFloatData() && distanceField.getScalars().getFloatData().getDataCount() > i) {
                                    record.put("distance", distanceField.getScalars().getFloatData().getData(i));
                                } else if (distanceField.getScalars().hasDoubleData() && distanceField.getScalars().getDoubleData().getDataCount() > i) {
                                    record.put("distance", (float) distanceField.getScalars().getDoubleData().getData(i));
                                } else {
                                    record.put("distance", 0.0f); // Default distance
                                }
                            } else {
                                record.put("distance", 0.0f); // Default distance
                            }
                            
                            // Determine match type based on similarity
                            float distance = (Float) record.get("distance");
                            if (distance < 0.1f) {
                                record.put("match_type", "exact");
                            } else if (distance < 0.3f) {
                                record.put("match_type", "high_similarity");
                            } else if (distance < 0.5f) {
                                record.put("match_type", "medium_similarity");
                            } else {
                                record.put("match_type", "low_similarity");
                            }
                            
                            // Extract metadata fields
                            for (String fieldName : outFields) {
                                if (!fieldName.equals("id") && !fieldName.equals(textField)) {
                                    var fieldData = resultData.getFieldsDataList().stream()
                                        .filter(f -> f.getFieldName().equals(fieldName))
                                        .findFirst()
                                        .orElse(null);
                                    if (fieldData != null) {
                                        Object value = extractFieldValue(fieldData, i);
                                        record.put(fieldName, value);
                                    }
                                }
                            }
                            
                            searchResults.add(record);
                        }

                        // Build the final response
                        Map<String, Object> searchResponse = new HashMap<>();
                        searchResponse.put("found", true);
                        searchResponse.put("search_text", text);
                        searchResponse.put("total_results", searchResults.size());
                        searchResponse.put("results", searchResults);
                        searchResponse.put("message", String.format("Found %d relevant records", searchResults.size()));

                        log.info("Successfully searched records with {} results", searchResults.size());
                        return Mono.just(searchResponse);
                        
                    } catch (Exception e) {
                        log.error("Failed to perform semantic search: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                });
                
        } catch (Exception e) {
            log.error("Failed to search records in collection {}: {}", collectionName, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Map<String, Object>> getCollectionDetails() {
        log.info("Getting detailed collection information");
        try {
            log.info("Calling showCollections with database 'default'");
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder()
                    .withDatabaseName("default")
                    .build());
            
            log.info("ShowCollections response status: {}", response.getStatus());
            
            // Check if the response is successful
            if (response.getStatus() != 0) {
                String errorMessage = response.getMessage();
                if (errorMessage == null) {
                    errorMessage = "Unknown error occurred while listing collections";
                }
                log.error("ShowCollections failed with status {}: {}", response.getStatus(), errorMessage);
                
                // Try without database specification as fallback
                log.info("Trying showCollections without database specification as fallback");
                R<ShowCollectionsResponse> fallbackResponse = milvusClient.showCollections(ShowCollectionsParam.newBuilder()
                        .build());
                
                if (fallbackResponse.getStatus() != 0) {
                    String fallbackErrorMessage = fallbackResponse.getMessage();
                    if (fallbackErrorMessage == null) {
                        fallbackErrorMessage = "Unknown error occurred while listing collections";
                    }
                    log.error("Fallback ShowCollections also failed with status {}: {}", fallbackResponse.getStatus(), fallbackErrorMessage);
                    return Mono.error(new RuntimeException("Failed to list collections: " + fallbackErrorMessage));
                }
                
                if (fallbackResponse.getData() == null) {
                    log.error("Fallback ShowCollections returned null data");
                    return Mono.error(new RuntimeException("No data received from Milvus"));
                }
                
                response = fallbackResponse;
                log.info("Using fallback response without database specification");
            }
            
            if (response.getData() == null) {
                log.error("ShowCollections returned null data");
                return Mono.error(new RuntimeException("No data received from Milvus"));
            }
            
            List<String> allCollections = response.getData().getCollectionNamesList();
            
            log.info("Found {} collections in current database context: {}", allCollections.size(), allCollections);
            
            if (allCollections.isEmpty()) {
                log.warn("No collections found. This might indicate a database context issue.");
            }
            
            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> collections = new ArrayList<>();
            
            for (String collectionName : allCollections) {
                try {
                    // Get collection properties
                    R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                        DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .withDatabaseName("default")
                            .build()
                    );
                    
                    Map<String, Object> collectionInfo = new HashMap<>();
                    collectionInfo.put("name", collectionName);
                    
                    // Get teamId from properties
                    for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                        if ("teamId".equals(property.getKey())) {
                            collectionInfo.put("teamId", property.getValue());
                            break;
                        }
                    }
                    
                    // Get schema information
                    List<Map<String, Object>> fields = new ArrayList<>();
                    for (io.milvus.grpc.FieldSchema field : describeResponse.getData().getSchema().getFieldsList()) {
                        Map<String, Object> fieldInfo = new HashMap<>();
                        fieldInfo.put("name", field.getName());
                        fieldInfo.put("dataType", field.getDataType().name());
                        fieldInfo.put("isPrimary", field.getIsPrimaryKey());
                        if (field.getTypeParamsCount() > 0) {
                            // Convert Protobuf type params to simple map
                            Map<String, String> typeParams = new HashMap<>();
                            for (KeyValuePair param : field.getTypeParamsList()) {
                                typeParams.put(param.getKey(), param.getValue());
                            }
                            fieldInfo.put("typeParams", typeParams);
                        }
                        fields.add(fieldInfo);
                    }
                    collectionInfo.put("schema", fields);
                    
                    // Get collection statistics
                    try {
                        R<GetCollectionStatisticsResponse> statsResponse = milvusClient.getCollectionStatistics(
                            GetCollectionStatisticsParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withDatabaseName("default")
                                .build()
                        );
                        // Extract row count from the response
                        long rowCount = 0;
                        for (KeyValuePair stat : statsResponse.getData().getStatsList()) {
                            if ("row_count".equals(stat.getKey())) {
                                rowCount = Long.parseLong(stat.getValue());
                                break;
                            }
                        }
                        collectionInfo.put("rowCount", rowCount);
                    } catch (Exception e) {
                        log.warn("Failed to get row count for collection {}: {}", collectionName, e.getMessage());
                        collectionInfo.put("rowCount", "unknown");
                    }
                    
                    collections.add(collectionInfo);
                    
                } catch (Exception e) {
                    log.warn("Failed to get details for collection {}: {}", collectionName, e.getMessage());
                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("name", collectionName);
                    errorInfo.put("error", e.getMessage());
                    collections.add(errorInfo);
                }
            }
            
            result.put("collections", collections);
            result.put("totalCollections", collections.size());
            result.put("database", "default");
            
            log.info("Retrieved details for {} collections", collections.size());
            return Mono.just(result);
        } catch (Exception e) {
            log.error("Failed to get collection details: {}", e.getMessage());
            return Mono.error(e);
        }
    }
}
