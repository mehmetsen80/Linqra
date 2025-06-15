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
import io.milvus.param.dml.QueryParam;
import io.milvus.grpc.QueryResults;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.collection.AlterCollectionParam;
import io.milvus.param.dml.DeleteParam;

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
                            List<InsertParam.Field> fields = new ArrayList<>();
                            record.forEach((key, value) -> {
                                if (!key.equals(textField)) {
                                    fields.add(new InsertParam.Field(key, List.of(value)));
                                }
                            });
                            fields.add(new InsertParam.Field("embedding", List.of(embedding)));
                            fields.add(new InsertParam.Field(textField, List.of(text)));
                            fields.add(new InsertParam.Field("teamId", List.of(teamId)));

                            InsertParam insertParam = InsertParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .withFields(fields)
                                    .build();
                            milvusClient.insert(insertParam);
                            log.info("Stored record in collection {}", collectionName);
                            return Mono.just(Map.of("message", "Record stored successfully in collection " + collectionName));
                        } catch (Exception e) {
                            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage());
                            return Mono.error(e);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage());
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
                    if ("openai-embed".equals(targetTool)) {
                        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                        return (List<Float>) data.get(0).get("embedding");
                    } else if ("huggingface".equals(targetTool)) {
                        return (List<Float>) result.get("embedding");
                    } else if ("gemini-embed".equals(targetTool)) {
                        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                        return (List<Float>) data.get(0).get("embedding");
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

            SearchResultData result = results.getResults();
            
            // Process all results since they're already filtered by teamId
            for (int i = 0; i < result.getNumQueries(); i++) {
                ids.add(((List<?>) result.getField(result.getDescriptorForType().findFieldByName(idField))).get(i));
                documents.add(((List<?>) result.getField(result.getDescriptorForType().findFieldByName(textField))).get(i));
                Map<String, Object> metadata = new HashMap<>();
                for (String field : outputFields) {
                    if (!field.equals("embedding")) {
                        metadata.put(field, ((List<?>) result.getField(result.getDescriptorForType().findFieldByName(field))).get(i));
                    }
                }
                metadatas.add(metadata);
                distances.add((Float) ((List<?>) result.getField(result.getDescriptorForType().findFieldByName("distance"))).get(i));
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("ids", ids);
            resultMap.put("documents", documents);
            resultMap.put("metadatas", metadatas);
            resultMap.put("distances", distances);

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
                .collect(Collectors.toList());

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
                .collect(Collectors.toList());

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
}
