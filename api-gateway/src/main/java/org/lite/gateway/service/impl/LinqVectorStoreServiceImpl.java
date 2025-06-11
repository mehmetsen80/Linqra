package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.service.LinqService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqVectorStoreService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
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

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqVectorStoreServiceImpl implements LinqVectorStoreService {

    private static final String MILVUS_HOST = "localhost";
    private static final int MILVUS_PORT = 19530;
    private static final String EMBEDDING_FIELD = "embedding";
    private static final IndexType INDEX_TYPE = IndexType.HNSW;
    private static final MetricType METRIC_TYPE = MetricType.COSINE;
    private static final int INDEX_PARAM_M = 8;
    private static final int INDEX_PARAM_EF_CONSTRUCTION = 64;
    private static final int SEARCH_PARAM_EF = 64;

    private final LinqService linqService;
    private final LinqToolService linqToolService;
    private MilvusServiceClient milvusClient;

    @PostConstruct
    public void init() {
        try {
            // Connect to Milvus
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(MILVUS_HOST)
                    .withPort(MILVUS_PORT)
                    .build();
            milvusClient = new MilvusServiceClient(connectParam);
            log.info("Connected to Milvus at {}:{}", MILVUS_HOST, MILVUS_PORT);
        } catch (Exception e) {
            log.error("Failed to initialize Milvus client: {}", e.getMessage(), e);
            throw new RuntimeException("Milvus initialization failed", e);
        }
    }

    @Override
    public Mono<Void> createCollection(String collectionName, List<Map<String, Object>> schemaFields, String description, String teamId) {
        log.info("Creating Milvus collection {} for team {}", collectionName, teamId);
        try {
            // Check if collection already exists
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            
            if (hasCollection.getData()) {
                log.info("Collection {} already exists, skipping creation", collectionName);
                return Mono.empty();
            }

            List<FieldType> fields = schemaFields.stream().map(this::mapToFieldType).toList();

            CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription(description != null ? description : "")
                    .withShardsNum(2)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withSchema(CollectionSchemaParam.newBuilder()
                            .withFieldTypes(fields)
                            .build());
            
            milvusClient.createCollection(builder.build());
            log.info("Created Milvus collection: {}", collectionName);

            // Create index on embedding field
            String embeddingField = schemaFields.stream()
                    .filter(f -> "FloatVector".equals(f.get("dataType")))
                    .findFirst()
                    .map(f -> (String) f.get("fieldName"))
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
            return Mono.empty();
        } catch (Exception e) {
            log.error("Failed to create collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    private FieldType mapToFieldType(Map<String, Object> schemaField) {
        String fieldName = (String) schemaField.get("fieldName");
        String dataType = (String) schemaField.get("dataType");
        Map<String, String> typeParams = (Map<String, String>) schemaField.getOrDefault("typeParams", new HashMap<>());
        Boolean isPrimaryKey = (Boolean) schemaField.getOrDefault("isPrimaryKey", false);
        Boolean isAutoID = (Boolean) schemaField.getOrDefault("isAutoID", false);

        FieldType.Builder builder = FieldType.newBuilder()
                .withName(fieldName)
                .withDataType(DataType.valueOf(dataType));

        if ("VarChar".equals(dataType) || "VARCHAR".equals(dataType)) {
            builder.withMaxLength(Integer.parseInt(typeParams.getOrDefault("maxLength", "256")));
        } else if ("FloatVector".equals(dataType)) {
            builder.withDimension(Integer.parseInt(typeParams.getOrDefault("dim", "1536")));
        }

        if (isPrimaryKey) {
            builder.withPrimaryKey(true);
        }
        if (isAutoID) {
            builder.withAutoID(true);
        }

        return builder.build();
    }

    @Override
    public Mono<List<Float>> getEmbedding(String text, String targetTool, String modelType, String teamId) {
        log.info("Generating embedding for text: {} using {} (model: {}, team: {})", text, targetTool, modelType, teamId);
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
                })
                .doOnError(error -> log.error("Failed to generate embedding: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> storeRecord(String collectionName, Map<String, Object> record, String targetTool, String modelType, String textField, String teamId) {
        log.info("Storing record in collection {} (team: {})", collectionName, teamId);
        String text = (String) record.get(textField);
        if (text == null) {
            return Mono.error(new IllegalArgumentException("Text field " + textField + " not found in record"));
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

                        InsertParam insertParam = InsertParam.newBuilder()
                                .withCollectionName(collectionName)
                                .withFields(fields)
                                .build();
                        milvusClient.insert(insertParam);
                        log.info("Stored record in collection {}", collectionName);
                        return Mono.empty();
                    } catch (Exception e) {
                        log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage());
                        return Mono.error(e);
                    }
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
            ids.add(((List<?>) result.getField(result.getDescriptorForType().findFieldByName(idField))).get(0));
            documents.add(((List<?>) result.getField(result.getDescriptorForType().findFieldByName(textField))).get(0));
            Map<String, Object> metadata = new HashMap<>();
            for (String field : outputFields) {
                if (!field.equals("embedding")) {
                    metadata.put(field, ((List<?>) result.getField(result.getDescriptorForType().findFieldByName(field))).get(0));
                }
            }
            metadatas.add(metadata);
            distances.add((Float) ((List<?>) result.getField(result.getDescriptorForType().findFieldByName("distance"))).get(0));

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("ids", ids);
            resultMap.put("documents", documents);
            resultMap.put("metadatas", metadatas);
            resultMap.put("distances", distances);

            log.info("Retrieved {} results from collection {}", ids.size(), collectionName);
            return Mono.just(resultMap);
        } catch (Exception e) {
            log.error("Failed to query collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Void> populateCollection(String collectionName, String sourceService, String intent, String action, String textField, String teamId) {
        log.info("Populating collection {} from service {} (team: {})", collectionName, sourceService, teamId);
        LinqRequest request = new LinqRequest();
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(sourceService);
        link.setAction(action);
        request.setLink(link);

        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(intent);
        request.setQuery(query);

        return linqService.processLinqRequest(request)
                .flatMap(response -> {
                    List<Map<String, Object>> records = (List<Map<String, Object>>) response.getResult();
                    return Flux.fromIterable(records)
                            .flatMap(record -> storeRecord(collectionName, record, "huggingface", "all-MiniLM-L6-v2", textField, teamId))
                            .then();
                })
                .doOnSuccess(v -> log.info("Finished populating collection {}", collectionName))
                .doOnError(error -> log.error("Failed to populate collection {}: {}", collectionName, error.getMessage()));
    }
}
