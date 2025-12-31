package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.springframework.beans.factory.annotation.Value;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.dto.MilvusCollectionSchemaInfo;
import org.lite.gateway.dto.MilvusCollectionVerificationResponse;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.validation.validator.MilvusSchemaValidator;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.grpc.KeyValuePair;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.FieldSchema;
import io.milvus.param.collection.AlterCollectionParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.param.dml.DeleteParam;
import io.milvus.grpc.MutationResult;
import io.milvus.response.MutationResultWrapper;
import io.milvus.param.dml.QueryParam;
import io.milvus.grpc.QueryResults;
import io.milvus.response.QueryResultsWrapper;
import jakarta.annotation.PostConstruct;
import io.milvus.grpc.SearchResults;
import org.springframework.util.StringUtils;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.util.AuditLogHelper;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqMilvusStoreServiceImpl implements LinqMilvusStoreService {

    private final LinqLlmModelService linqLlmModelService;
    private final MilvusServiceClient milvusClient;
    private final AgentTaskRepository agentTaskRepository;
    private final ChunkEncryptionService chunkEncryptionService;
    private final AuditLogHelper auditLogHelper;
    private final ConcurrentHashMap<String, MilvusCollectionSchemaInfo> collectionSchemaCache = new ConcurrentHashMap<>();

    @Value("${milvus.uri:#{null}}")
    private String milvusUri;

    @PostConstruct
    public void checkConnection() {
        try {
            log.info("🔍 Checking Milvus Connection...");
            log.info("   URI: {}", milvusUri != null ? milvusUri : "LOCAL (null URI)");

            R<ShowCollectionsResponse> response = milvusClient
                    .showCollections(ShowCollectionsParam.newBuilder().build());

            if (response.getStatus() != 0) {
                log.error("❌ Failed to list collections: {}", response.getMessage());
            } else {
                List<String> names = response.getData().getCollectionNamesList();
                log.info("✅ Found {} collections: {}", names.size(), names);

                // Detailed check for a few
                for (String name : names) {
                    R<DescribeCollectionResponse> desc = milvusClient
                            .describeCollection(DescribeCollectionParam.newBuilder()
                                    .withCollectionName(name)
                                    .build());
                    if (desc.getStatus() == 0) {
                        log.info("   - {} (ID: {}, Loaded: {})", name, desc.getData().getCollectionID(),
                                desc.getData());
                    } else {
                        log.warn("   - {} (Describe Failed: {})", name, desc.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Milvus connection check failed", e);
        }
    }

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
            Map.entry("SPARSE_FLOAT_VECTOR", DataType.SparseFloatVector));

    private static final List<String> KNOWLEDGE_HUB_DEFAULT_OUT_FIELDS = List.of(
            "documentId",
            "collectionId",
            "chunkId",
            "chunkIndex",
            "fileName",
            "pageNumbers",
            "title",
            "author",
            "subject",
            "language",
            "teamId",
            "tokenCount",
            "qualityScore",
            "startPosition",
            "endPosition",
            "createdAt",
            "category",
            "metadataOnly",
            "documentType",
            "mimeType",
            "collectionType",
            "encryptionKeyVersion");

    @Override
    public Mono<Map<String, String>> createCollection(String collectionName,
            List<Map<String, Object>> schemaFields,
            String description,
            String teamId,
            String collectionType,
            Map<String, String> properties) {
        log.info("Creating Milvus collection {} for team {}", collectionName, teamId);
        LocalDateTime startTime = LocalDateTime.now();

        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build());

            if (hasCollection.getData()) {
                log.info("Collection {} already exists, skipping creation but ensuring it is loaded", collectionName);

                // Ensure it is loaded (critical for pre-existing/script-created collections)
                milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)

                        .build());

                // Log skipped creation attempt
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> auditContext = new HashMap<>();
                auditContext.put("collectionName", collectionName);
                auditContext.put("teamId", teamId);
                auditContext.put("collectionType", collectionType);
                auditContext.put("reason", "Collection already exists");
                auditContext.put("durationMs", durationMs);

                // Chain audit logging before returning
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_CREATED,
                        AuditActionType.CREATE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection creation skipped - collection '%s' already exists", collectionName),
                        auditContext,
                        null,
                        null,
                        AuditResultType.SUCCESS)
                        .doOnError(auditError -> log.error("Failed to log audit event (collection already exists): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .thenReturn(Map.of("message",
                                "Collection " + collectionName + " already exists, skipping creation"));
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

                    .withExtraParam(
                            "{\"M\":" + INDEX_PARAM_M + ",\"efConstruction\":" + INDEX_PARAM_EF_CONSTRUCTION + "}")
                    .build();

            milvusClient.createIndex(indexParam);
            log.info("Created index on field: {}", embeddingField);

            // Load collection
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build());
            log.info("Loaded Milvus collection: {}", collectionName);

            // Set teamId as a collection property
            Map<String, String> collectionProperties = new HashMap<>();
            collectionProperties.put("teamId", teamId);
            if (collectionType != null && !collectionType.isBlank()) {
                collectionProperties.put("collectionType", collectionType);
            }
            if (properties != null) {
                properties.forEach((key, value) -> {
                    if (key != null && value != null) {
                        collectionProperties.put(key, value);
                    }
                });
            }

            AlterCollectionParam.Builder alterBuilder = AlterCollectionParam.newBuilder()
                    .withCollectionName(collectionName);
            collectionProperties.forEach(alterBuilder::withProperty);
            milvusClient.alterCollection(alterBuilder.build());
            log.info("Set collection properties {} for {}", collectionProperties, collectionName);

            // Log successful collection creation
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("collectionName", collectionName);
            auditContext.put("teamId", teamId);
            auditContext.put("collectionType", collectionType);
            auditContext.put("description", description);
            auditContext.put("schemaFieldCount", schemaFields.size());
            auditContext.put("embeddingField", embeddingField);
            auditContext.put("indexType", INDEX_TYPE.name());
            auditContext.put("metricType", METRIC_TYPE.name());
            auditContext.put("shardsNum", SHARDS_NUM);
            auditContext.put("properties", collectionProperties);
            auditContext.put("durationMs", durationMs);
            auditContext.put("creationTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_CREATED,
                    AuditActionType.CREATE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Milvus collection '%s' created successfully for team '%s'", collectionName, teamId),
                    auditContext,
                    null,
                    null)
                    .doOnError(auditError -> log.error("Failed to log audit event (collection created): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .thenReturn(Map.of("message", "Collection " + collectionName + " created successfully"));
        } catch (Exception e) {
            log.error("Failed to create collection {}: {}", collectionName, e.getMessage());

            // Log failed collection creation
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("collectionName", collectionName);
            errorContext.put("teamId", teamId);
            errorContext.put("collectionType", collectionType);
            errorContext.put("description", description);
            errorContext.put("schemaFieldCount", schemaFields != null ? schemaFields.size() : 0);
            errorContext.put("error", e.getMessage());
            errorContext.put("errorType", e.getClass().getSimpleName());
            errorContext.put("durationMs", durationMs);
            errorContext.put("failureTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning error
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_CREATED,
                    AuditActionType.CREATE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Milvus collection creation failed for '%s': %s", collectionName, e.getMessage()),
                    errorContext,
                    null,
                    null,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (collection creation failed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .then(Mono.error(e));
        }
    }

    @Override
    public Mono<MilvusCollectionSchemaInfo> getCollectionSchema(String collectionName) {
        return Mono.fromCallable(() -> {
            MilvusCollectionSchemaInfo cached = collectionSchemaCache.get(collectionName);
            if (cached != null) {
                return cached;
            }

            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            if (describeResponse.getStatus() != 0) {
                throw new IllegalStateException(
                        "Failed to describe collection " + collectionName + ": " + describeResponse.getMessage());
            }

            DescribeCollectionResponse data = describeResponse.getData();
            if (data == null || data.getSchema() == null) {
                throw new IllegalStateException("Describe collection returned no schema for " + collectionName);
            }

            Set<String> fieldNames = data.getSchema().getFieldsList().stream()
                    .map(FieldSchema::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            String vectorFieldName = data.getSchema().getFieldsList().stream()
                    .filter(field -> field.getDataType() == DataType.FloatVector
                            || field.getDataType() == DataType.BinaryVector
                            || field.getDataType() == DataType.Float16Vector
                            || field.getDataType() == DataType.BFloat16Vector
                            || field.getDataType() == DataType.SparseFloatVector)
                    .map(FieldSchema::getName)
                    .findFirst()
                    .orElse("embedding");

            FieldSchema textFieldSchema = data.getSchema().getFieldsList().stream()
                    .filter(field -> "text".equals(field.getName()))
                    .findFirst()
                    .orElse(null);

            String textFieldName = textFieldSchema != null ? textFieldSchema.getName() : "text";
            Integer textFieldMaxLength = null;
            if (textFieldSchema != null) {
                for (KeyValuePair param : textFieldSchema.getTypeParamsList()) {
                    if ("max_length".equalsIgnoreCase(param.getKey()) || "maxLength".equalsIgnoreCase(param.getKey())) {
                        try {
                            textFieldMaxLength = Integer.parseInt(param.getValue());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            String collectionType = data.getPropertiesList().stream()
                    .filter(property -> "collectionType".equals(property.getKey()))
                    .map(KeyValuePair::getValue)
                    .findFirst()
                    .orElse(null);

            MilvusCollectionSchemaInfo schemaInfo = MilvusCollectionSchemaInfo.builder()
                    .collectionName(collectionName)
                    .collectionType(collectionType)
                    .fieldNames(Collections.unmodifiableSet(fieldNames))
                    .vectorFieldName(vectorFieldName)
                    .textFieldName(textFieldName)
                    .textFieldMaxLength(textFieldMaxLength)
                    .build();

            collectionSchemaCache.put(collectionName, schemaInfo);
            return schemaInfo;
        })
                .subscribeOn(Schedulers.boundedElastic());
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

    private MilvusCollectionInfo buildCollectionInfo(String collectionName, String teamId,
            DescribeCollectionResponse describeResponse) {
        Integer vectorDimension = null;
        String vectorFieldName = null;
        String description = null;
        String collectionType = null;
        Map<String, String> properties = new HashMap<>();
        Long rowCount = null;

        if (describeResponse != null && describeResponse.hasSchema()) {
            try {
                description = describeResponse.getSchema().getDescription();
            } catch (Exception ignored) {
                // ignore description extraction failures
            }

            for (FieldSchema fieldSchema : describeResponse.getSchema().getFieldsList()) {
                DataType dataType = fieldSchema.getDataType();
                if (dataType == DataType.FloatVector || dataType == DataType.Float16Vector
                        || dataType == DataType.BFloat16Vector || dataType == DataType.SparseFloatVector
                        || dataType == DataType.BinaryVector) {
                    vectorFieldName = fieldSchema.getName();
                    for (KeyValuePair param : fieldSchema.getTypeParamsList()) {
                        if ("dim".equalsIgnoreCase(param.getKey())) {
                            try {
                                vectorDimension = Integer.parseInt(param.getValue());
                            } catch (NumberFormatException ex) {
                                log.warn("Unable to parse vector dimension '{}' for collection {}", param.getValue(),
                                        collectionName);
                            }
                            break;
                        }
                    }
                    break;
                }
            }
        }

        if (describeResponse != null) {
            for (KeyValuePair property : describeResponse.getPropertiesList()) {
                properties.put(property.getKey(), property.getValue());
                if ("teamId".equals(property.getKey())) {
                    teamId = property.getValue();
                }
                if ("collectionType".equals(property.getKey())) {
                    collectionType = property.getValue();
                }
            }
        }

        boolean nameLocked = isCollectionNameLocked(collectionName);
        if (nameLocked) {
            properties.put("collectionNameLocked", "true");
        } else {
            properties.putIfAbsent("collectionNameLocked", "false");
        }

        if (collectionType == null && describeResponse != null && describeResponse.hasSchema()) {
            Set<String> fieldNames = describeResponse.getSchema().getFieldsList().stream()
                    .map(FieldSchema::getName)
                    .collect(Collectors.toSet());
            if (fieldNames.containsAll(Set.of("embedding", "text", "documentId", "collectionId", "chunkId"))) {
                collectionType = "KNOWLEDGE_HUB";
            }
        }

        long fetchedRowCount = getCollectionRowCount(collectionName);
        if (fetchedRowCount >= 0) {
            rowCount = fetchedRowCount;
            properties.put("rowCount", String.valueOf(fetchedRowCount));
        }

        return MilvusCollectionInfo.builder()
                .name(collectionName)
                .teamId(teamId)
                .vectorDimension(vectorDimension)
                .vectorFieldName(vectorFieldName)
                .description(description)
                .collectionType(collectionType)
                .properties(properties)
                .nameLocked(nameLocked)
                .rowCount(rowCount)
                .build();
    }

    private boolean isCollectionNameLocked(String collectionName) {
        try {
            String regex = "^/api/milvus/collections/" + Pattern.quote(collectionName) + "(/.*)?$";
            return Boolean.TRUE.equals(agentTaskRepository.existsByWorkflowIntentMatching(regex).block());
        } catch (Exception e) {
            log.warn("Unable to determine usage for collection {}: {}", collectionName, e.getMessage());
            return false;
        }
    }

    private long getCollectionRowCount(String collectionName) {
        try {
            R<GetCollectionStatisticsResponse> statsResponse = milvusClient.getCollectionStatistics(
                    GetCollectionStatisticsParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());
            if (statsResponse.getData() != null) {
                for (KeyValuePair stat : statsResponse.getData().getStatsList()) {
                    if ("row_count".equals(stat.getKey())) {
                        return Long.parseLong(stat.getValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to fetch row count for collection {}: {}", collectionName, e.getMessage());
        }
        return -1;
    }

    @Override
    public Mono<Map<String, String>> storeRecord(String collectionName, Map<String, Object> record, String target,
            String modelName, String textField, String teamId, List<Float> embedding) {
        log.debug("Storing record in collection {} for team {} (embedding: {})", collectionName, teamId,
                embedding != null ? "pre-computed" : "will generate");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            String text = (String) record.get(textField);
            if (text == null) {
                // Log validation failure
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("textField", textField);
                errorContext.put("error", "Text field " + textField + " not found in record");
                errorContext.put("durationMs", durationMs);

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.CHUNK_CREATED,
                        AuditActionType.CREATE,
                        AuditResourceType.CHUNK,
                        collectionName,
                        String.format("Record storage failed in collection '%s' - text field not found",
                                collectionName),
                        errorContext,
                        null,
                        null,
                        AuditResultType.FAILED)
                        .doOnError(auditError -> log.error("Failed to log audit event (text field not found): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(
                                new IllegalArgumentException("Text field " + textField + " not found in record")));
            }

            if (teamId == null || teamId.trim().isEmpty()) {
                // Log validation failure
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("textField", textField);
                errorContext.put("error", "teamId cannot be null or empty");
                errorContext.put("durationMs", durationMs);

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.CHUNK_CREATED,
                        AuditActionType.CREATE,
                        AuditResourceType.CHUNK,
                        collectionName,
                        String.format("Record storage failed in collection '%s' - teamId is required", collectionName),
                        errorContext,
                        null,
                        null,
                        AuditResultType.FAILED)
                        .doOnError(auditError -> log.error("Failed to log audit event (teamId required): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new IllegalArgumentException("teamId cannot be null or empty")));
            }

            // Extract documentId and collectionId from record if available
            String documentId = record.get("documentId") != null ? record.get("documentId").toString() : null;
            String collectionId = record.get("collectionId") != null ? record.get("collectionId").toString() : null;

            // If pre-computed embedding is provided, use it directly
            if (embedding != null && !embedding.isEmpty()) {
                log.debug("✅ Using pre-computed embedding (size: {}) - skipping embedding generation",
                        embedding.size());
                return storeWithEmbedding(collectionName, record, embedding, textField, teamId, startTime, documentId,
                        collectionId, target, modelName, true);
            }

            // Otherwise, generate embedding as usual
            log.debug("🔄 Generating new embedding using target: {}, model: {}", target, modelName);
            return getEmbedding(text, target, modelName, teamId)
                    .flatMap(generatedEmbedding -> storeWithEmbedding(collectionName, record, generatedEmbedding,
                            textField, teamId, startTime, documentId, collectionId, target, modelName, false));
        } catch (Exception e) {
            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage(), e);

            // Log storage failure
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("collectionName", collectionName);
            errorContext.put("teamId", teamId);
            errorContext.put("error", e.getMessage());
            errorContext.put("errorType", e.getClass().getSimpleName());
            errorContext.put("durationMs", durationMs);
            errorContext.put("failureTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning error
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.CHUNK_CREATED,
                    AuditActionType.CREATE,
                    AuditResourceType.CHUNK,
                    collectionName,
                    String.format("Record storage failed in collection '%s': %s", collectionName, e.getMessage()),
                    errorContext,
                    null,
                    null,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (record storage failed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .then(Mono.error(e));
        }
    }

    /**
     * Internal method to store a record with a given embedding
     */
    private Mono<Map<String, String>> storeWithEmbedding(String collectionName, Map<String, Object> record,
            List<Float> embedding, String textField, String teamId, LocalDateTime startTime, String documentId,
            String collectionId, String target, String modelName, boolean preComputedEmbedding) {
        try {
            String text = (String) record.get(textField);

            // First get the collection schema to ensure we provide all fields
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            List<InsertParam.Field> fields = new ArrayList<>();

            // Add ID field with unique identifier per record
            long uniqueId = Math.abs(UUID.randomUUID().getMostSignificantBits());
            fields.add(new InsertParam.Field("id", Collections.singletonList(uniqueId)));

            // Add created_at field with current timestamp
            fields.add(new InsertParam.Field("created_at", Collections.singletonList(System.currentTimeMillis())));

            // Add embedding field
            fields.add(new InsertParam.Field("embedding", Collections.singletonList(embedding)));

            // Add text field
            fields.add(new InsertParam.Field(textField, Collections.singletonList(text)));

            // Add all other fields from the schema, using null if not provided in the
            // record
            for (io.milvus.grpc.FieldSchema fieldSchema : describeResponse.getData().getSchema().getFieldsList()) {
                String fieldName = fieldSchema.getName();
                if (!fieldName.equals("id") && !fieldName.equals("embedding") && !fieldName.equals(textField)) {
                    Object value = record.get(fieldName);

                    // Check if field is required (not nullable) and value is null
                    String dataTypeName = fieldSchema.getDataType() != null ? fieldSchema.getDataType().name() : "";
                    if (value == null && Boolean.FALSE.equals(fieldSchema.getNullable())) {
                        // For non-nullable fields, provide a default value based on the data type
                        value = determineDefaultValue(fieldName, dataTypeName);
                    }

                    // If value is null, pass it through as-is
                    Object convertedValue = value;
                    if (value != null) {
                        try {
                            convertedValue = coerceValueForType(value, dataTypeName, fieldName);
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse number for field {} with value '{}': {}. Using default value.",
                                    fieldName, value, e.getMessage());
                            // Use default values for number fields when parsing fails
                            convertedValue = fallbackDefaultValueForParsing(dataTypeName, value);
                        } catch (Exception e) {
                            log.warn("Failed to convert value for field {}: {}. Using original value.", fieldName,
                                    e.getMessage());
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

            log.debug("Inserting record with fields: {}", fields.stream()
                    .map(f -> f.getName() + ": " + f.getValues().getFirst())
                    .collect(Collectors.joining(", ")));

            R<io.milvus.grpc.MutationResult> insertResponse = milvusClient.insert(insertParam);
            if (insertResponse.getStatus() != 0) {
                log.error("Milvus insert returned non-zero status {} for collection {}", insertResponse.getStatus(),
                        collectionName);

                // Log failed storage
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("documentId", documentId);
                errorContext.put("collectionId", collectionId);
                errorContext.put("embeddingSize", embedding != null ? embedding.size() : 0);
                errorContext.put("preComputedEmbedding", preComputedEmbedding);
                errorContext.put("textField", textField);
                errorContext.put("milvusStatus", insertResponse.getStatus());
                errorContext.put("error", insertResponse.getMessage());
                errorContext.put("durationMs", durationMs);
                errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.CHUNK_CREATED,
                        AuditActionType.CREATE,
                        AuditResourceType.CHUNK,
                        documentId != null ? documentId : collectionName,
                        String.format(
                                "Record storage failed in collection '%s' - Milvus insert returned non-zero status",
                                collectionName),
                        errorContext,
                        documentId,
                        collectionId,
                        AuditResultType.FAILED)
                        .doOnError(auditError -> log.error("Failed to log audit event (Milvus insert failed): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new IllegalStateException(
                                "Milvus insert failed with status: " + insertResponse.getStatus())));
            }
            MutationResultWrapper insertResultWrapper = new MutationResultWrapper(insertResponse.getData());
            long insertCount = insertResultWrapper.getInsertCount();
            log.info("Stored record in collection {} (inserted {} vectors)", collectionName, insertCount);

            // Log successful storage
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("collectionName", collectionName);
            auditContext.put("teamId", teamId);
            auditContext.put("documentId", documentId);
            auditContext.put("collectionId", collectionId);
            auditContext.put("embeddingSize", embedding != null ? embedding.size() : 0);
            auditContext.put("preComputedEmbedding", preComputedEmbedding);
            auditContext.put("textField", textField);
            auditContext.put("target", target);
            auditContext.put("modelName", modelName);
            auditContext.put("insertCount", insertCount);
            auditContext.put("recordFieldCount", record.size());
            auditContext.put("durationMs", durationMs);
            auditContext.put("storageTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.CHUNK_CREATED,
                    AuditActionType.CREATE,
                    AuditResourceType.CHUNK,
                    documentId != null ? documentId : collectionName,
                    String.format("Record stored successfully in collection '%s' for team '%s'", collectionName,
                            teamId),
                    auditContext,
                    documentId,
                    collectionId)
                    .doOnError(auditError -> log.error("Failed to log audit event (record stored): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .thenReturn(Map.of("message", "Record stored successfully in collection " + collectionName));
        } catch (Exception e) {
            log.error("Failed to store record in collection {}: {}", collectionName, e.getMessage(), e);

            // Log storage failure
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("collectionName", collectionName);
            errorContext.put("teamId", teamId);
            errorContext.put("documentId", documentId);
            errorContext.put("collectionId", collectionId);
            errorContext.put("textField", textField);
            errorContext.put("error", e.getMessage());
            errorContext.put("errorType", e.getClass().getSimpleName());
            errorContext.put("durationMs", durationMs);
            errorContext.put("failureTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning error
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.CHUNK_CREATED,
                    AuditActionType.CREATE,
                    AuditResourceType.CHUNK,
                    documentId != null ? documentId : collectionName,
                    String.format("Record storage failed in collection '%s': %s", collectionName, e.getMessage()),
                    errorContext,
                    documentId,
                    collectionId,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (record storage failed in catch): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .then(Mono.error(e));
        }
    }

    private Object determineDefaultValue(String fieldName, String dataTypeName) {
        if (dataTypeName == null) {
            return null;
        }

        return switch (dataTypeName) {
            case "Int64" -> 0L;
            case "Int32", "Int8", "Int16" -> 0;
            case "Float" -> 0.0f;
            case "Double" -> 0.0;
            case "Bool" -> Boolean.FALSE;
            case "VarChar", "String", "Text" -> "";
            default -> throw new IllegalArgumentException("Field '" + fieldName
                    + "' is required but no value was provided and no default value is available for type "
                    + dataTypeName);
        };
    }

    private Object fallbackDefaultValueForParsing(String dataTypeName, Object originalValue) {
        if (dataTypeName == null) {
            return originalValue;
        }

        return switch (dataTypeName) {
            case "Int64" -> 0L;
            case "Int32", "Int16", "Int8" -> 0;
            case "Float" -> 0.0f;
            case "Double" -> 0.0;
            case "Bool" -> Boolean.FALSE;
            default -> originalValue;
        };
    }

    private Object coerceValueForType(Object value, String dataTypeName, String fieldName) {
        if (value == null || dataTypeName == null) {
            return value;
        }

        try {
            return switch (dataTypeName) {
                case "Int64" -> {
                    if (value instanceof Number number) {
                        yield number.longValue();
                    }
                    String strValue = value.toString().trim();
                    yield strValue.isEmpty() ? 0L : Long.parseLong(strValue);
                }
                case "Int32", "Int16", "Int8" -> {
                    if (value instanceof Number number) {
                        yield number.intValue();
                    }
                    String strValue = value.toString().trim();
                    yield strValue.isEmpty() ? 0 : Integer.parseInt(strValue);
                }
                case "Float" -> {
                    if (value instanceof Number number) {
                        yield number.floatValue();
                    }
                    String strValue = value.toString().trim();
                    yield strValue.isEmpty() ? 0.0f : Float.parseFloat(strValue);
                }
                case "Double" -> {
                    if (value instanceof Number number) {
                        yield number.doubleValue();
                    }
                    String strValue = value.toString().trim();
                    yield strValue.isEmpty() ? 0.0 : Double.parseDouble(strValue);
                }
                case "Bool" -> {
                    if (value instanceof Boolean boolValue) {
                        yield boolValue;
                    }
                    String strValue = value.toString().trim();
                    yield strValue.isEmpty() ? Boolean.FALSE : Boolean.parseBoolean(strValue);
                }
                case "VarChar", "String", "Text" -> value.toString();
                default -> value;
            };
        } catch (Exception e) {
            log.warn("Failed to coerce value for field {} of type {}: {}. Falling back to original value.", fieldName,
                    dataTypeName, e.getMessage());
            return value;
        }
    }

    @Override
    public Mono<List<Float>> getEmbedding(String text, String modelCategory, String modelName, String teamId) {
        log.debug("Getting embedding for text: {} with modelCategory: {} and model name: {} for team: {}", text,
                modelCategory, modelName, teamId);
        LinqRequest request = new LinqRequest();
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(modelCategory);
        link.setAction("generate");
        request.setLink(link);

        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent("embed");
        query.setParams(Map.of("text", text));

        LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
        llmConfig.setModel(modelName);
        query.setLlmConfig(llmConfig);
        request.setQuery(query);

        return linqLlmModelService.findByModelCategoryAndModelNameAndTeamId(modelCategory, modelName, teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Embedding modelCategory " + modelCategory
                        + " with model name " + modelName + " not found for team: " + teamId)))
                .flatMap(llmModel -> linqLlmModelService.executeLlmRequest(request, llmModel))
                .map(response -> {
                    Map<String, Object> result = (Map<String, Object>) response.getResult();
                    if (result == null) {
                        throw new IllegalStateException("Received null result from embedding service");
                    }

                    if (result.containsKey("error")) {
                        throw new IllegalStateException("Embedding service error: " + result.get("error"));
                    }

                    if ("openai-embed".equals(modelCategory)) {
                        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                        if (data == null || data.isEmpty()) {
                            throw new IllegalStateException("No data received from OpenAI embedding service");
                        }
                        List<Object> rawEmbedding = (List<Object>) data.getFirst().get("embedding");
                        return rawEmbedding.stream()
                                .map(value -> ((Number) value).floatValue())
                                .collect(Collectors.toList());
                    } else if ("huggingface-embed".equals(modelCategory)) {
                        List<Object> rawEmbedding = (List<Object>) result.get("embedding");
                        return rawEmbedding.stream()
                                .map(value -> ((Number) value).floatValue())
                                .collect(Collectors.toList());
                    } else if ("gemini-embed".equals(modelCategory)) {
                        // Gemini embedding response format: {embedding: {values: [...]}}
                        Map<String, Object> embedding = (Map<String, Object>) result.get("embedding");
                        if (embedding == null) {
                            throw new IllegalStateException("No embedding data received from Gemini embedding service");
                        }
                        List<Object> values = (List<Object>) embedding.get("values");
                        if (values == null || values.isEmpty()) {
                            throw new IllegalStateException(
                                    "No embedding values received from Gemini embedding service");
                        }
                        return values.stream()
                                .map(value -> ((Number) value).floatValue())
                                .collect(Collectors.toList());
                    } else if ("cohere-embed".equals(modelCategory)) {
                        // Cohere embedding response format: {embeddings: [[...]]}
                        List<List<Object>> embeddings = (List<List<Object>>) result.get("embeddings");
                        if (embeddings == null || embeddings.isEmpty()) {
                            throw new IllegalStateException("No embeddings received from Cohere embedding service");
                        }
                        List<Object> firstEmbedding = embeddings.getFirst();
                        if (firstEmbedding == null || firstEmbedding.isEmpty()) {
                            throw new IllegalStateException(
                                    "No embedding values received from Cohere embedding service");
                        }
                        return firstEmbedding.stream()
                                .map(value -> ((Number) value).floatValue())
                                .collect(Collectors.toList());
                    }
                    throw new IllegalArgumentException("Unsupported embedding modelCategory: " + modelCategory);
                });
    }

    @Override
    public Mono<Map<String, Object>> queryRecords(String collectionName, List<Float> embedding, int nResults,
            String[] outputFields, String teamId) {
        log.info("Querying collection {} for {} results (team: {})", collectionName, nResults, teamId);
        LocalDateTime startTime = LocalDateTime.now();

        return Mono.fromCallable(() -> {
            // Get collection schema to check which fields exist
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            List<String> availableFields = new ArrayList<>();
            if (describeResponse.getStatus() == 0 && describeResponse.getData() != null) {
                availableFields = describeResponse.getData().getSchema().getFieldsList().stream()
                        .map(io.milvus.grpc.FieldSchema::getName)
                        .collect(Collectors.toList());
            }

            // Ensure encryptionKeyVersion and teamId are included for decryption (only if
            // they exist in schema)
            List<String> outputFieldsList = new ArrayList<>(Arrays.asList(outputFields));
            if (availableFields.contains("encryptionKeyVersion")
                    && !outputFieldsList.contains("encryptionKeyVersion")) {
                outputFieldsList.add("encryptionKeyVersion");
            }
            if (availableFields.contains("teamId") && !outputFieldsList.contains("teamId")) {
                outputFieldsList.add("teamId");
            }

            SearchParam.Builder searchParamBuilder = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFloatVectors(List.of(embedding))
                    .withLimit((long) nResults)
                    .withMetricType(METRIC_TYPE)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withVectorFieldName(EMBEDDING_FIELD)
                    .withOutFields(outputFieldsList)
                    .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}");

            // Only add teamId filter if the field exists in the collection schema
            if (availableFields.contains("teamId") && teamId != null && !teamId.isEmpty()) {
                searchParamBuilder.withExpr("teamId == \"" + teamId + "\"");
            }

            SearchParam searchParam = searchParamBuilder.build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            // Check for "not loaded" error and retry
            if (searchResponse.getStatus() != 0) {
                String msg = searchResponse.getMessage();
                if (msg != null && (msg.contains("not loaded") || msg.contains("has not been loaded"))) {
                    log.warn("Collection {} is not loaded. Loading now and retrying search...", collectionName);

                    milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

                    // Retry search
                    searchResponse = milvusClient.search(searchParam);
                }
            }

            if (searchResponse.getStatus() != 0) {
                throw new IllegalStateException("Search failed: " + searchResponse.getMessage());
            }

            return searchResponse.getData();
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(results -> {
                    SearchResultData resultData = results.getResults();

                    String textField = Arrays.stream(outputFields)
                            .filter(f -> !f.equals("id") && !f.equals("embedding"))
                            .findFirst().orElse(outputFields[0]);

                    return Flux.range(0, (int) resultData.getNumQueries())
                            .concatMap(i -> {
                                // Extract ID
                                Object id = null;
                                var idFieldData = resultData.getFieldsDataList().stream()
                                        .filter(f -> f.getFieldName().equals("id"))
                                        .findFirst()
                                        .orElse(null);
                                if (idFieldData != null && idFieldData.getScalars().hasLongData()
                                        && idFieldData.getScalars().getLongData().getDataCount() > i) {
                                    id = idFieldData.getScalars().getLongData().getData(i);
                                }

                                final Object fId = id;

                                // Extract Distance
                                Float distance = 0.0f;
                                var distanceField = resultData.getFieldsDataList().stream()
                                        .filter(f -> f.getFieldName().equals("distance"))
                                        .findFirst()
                                        .orElse(null);

                                if (distanceField != null) {
                                    if (distanceField.getScalars().hasFloatData()
                                            && distanceField.getScalars().getFloatData().getDataCount() > i) {
                                        distance = distanceField.getScalars().getFloatData().getData(i);
                                    } else if (distanceField.getScalars().hasDoubleData()
                                            && distanceField.getScalars().getDoubleData().getDataCount() > i) {
                                        distance = (float) distanceField.getScalars().getDoubleData().getData(i);
                                    }
                                }
                                final Float fDistance = distance;

                                // Decrypt text
                                return decryptTextFromSearchResults(resultData, textField, i, teamId)
                                        .map(decryptedText -> {
                                            Map<String, Object> item = new HashMap<>();
                                            item.put("id", fId);
                                            item.put("text", decryptedText);
                                            item.put("distance", fDistance);
                                            return item;
                                        });
                            })
                            .collectList()
                            .flatMap(items -> {
                                List<Object> ids = items.stream().map(m -> m.get("id")).collect(Collectors.toList());
                                List<Object> documents = items.stream().map(m -> m.get("text"))
                                        .collect(Collectors.toList());
                                List<Object> distances = items.stream().map(m -> m.get("distance"))
                                        .collect(Collectors.toList());
                                List<Map<String, Object>> metadatas = new ArrayList<>();

                                Map<String, Object> resultMap = new HashMap<>();
                                resultMap.put("ids", ids);
                                resultMap.put("documents", documents);
                                resultMap.put("metadatas", metadatas);
                                resultMap.put("distances", distances);

                                log.info("Extracted search results - ids: {}, documents: {}, distances: {}",
                                        ids.size(), documents.size(), distances.size());

                                log.info("Retrieved {} results from collection {} for team {}", ids.size(),
                                        collectionName, teamId);

                                // Log successful query
                                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                                Map<String, Object> auditContext = new HashMap<>();
                                auditContext.put("collectionName", collectionName);
                                auditContext.put("teamId", teamId);
                                auditContext.put("nResults", nResults);
                                auditContext.put("resultCount", ids.size());
                                auditContext.put("embeddingSize", embedding != null ? embedding.size() : 0);
                                auditContext.put("outputFields", Arrays.asList(outputFields));
                                auditContext.put("durationMs", durationMs);
                                auditContext.put("queryTimestamp", LocalDateTime.now().toString());

                                return auditLogHelper.logDetailedEvent(
                                        AuditEventType.RAG_QUERY,
                                        AuditActionType.READ,
                                        AuditResourceType.COLLECTION,
                                        collectionName,
                                        String.format(
                                                "RAG query executed on collection '%s' for team '%s' - retrieved %d results",
                                                collectionName, teamId, ids.size()),
                                        auditContext,
                                        null,
                                        teamId,
                                        AuditResultType.SUCCESS)
                                        .doOnError(auditError -> log.error(
                                                "Failed to log audit event (query success): {}",
                                                auditError.getMessage(), auditError))
                                        .onErrorResume(auditError -> Mono.empty())
                                        .thenReturn(resultMap);
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to query collection {}: {}", collectionName, e.getMessage());

                    // Log query failure
                    long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    Map<String, Object> errorContext = new HashMap<>();
                    errorContext.put("collectionName", collectionName);
                    errorContext.put("teamId", teamId);
                    errorContext.put("nResults", nResults);
                    errorContext.put("embeddingSize", embedding != null ? embedding.size() : 0);
                    errorContext.put("outputFields", Arrays.asList(outputFields));
                    errorContext.put("error", e.getMessage());
                    errorContext.put("errorType", e.getClass().getSimpleName());
                    errorContext.put("durationMs", durationMs);
                    errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                    return auditLogHelper.logDetailedEvent(
                            AuditEventType.RAG_QUERY,
                            AuditActionType.READ,
                            AuditResourceType.COLLECTION,
                            collectionName,
                            String.format("RAG query failed for collection '%s': %s", collectionName, e.getMessage()),
                            errorContext,
                            null,
                            null,
                            AuditResultType.FAILED)
                            .doOnError(auditError -> log.error("Failed to log audit event (query failed): {}",
                                    auditError.getMessage(), auditError))
                            .onErrorResume(auditError -> Mono.empty())
                            .then(Mono.error(e));
                });
    }

    @Override
    public Mono<Map<String, String>> deleteCollection(String collectionName, String teamId) {
        log.info("Deleting collection {} for team {}", collectionName, teamId);
        LocalDateTime startTime = LocalDateTime.now();

        try {
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build());

            if (!hasCollection.getData()) {
                log.info("Collection {} does not exist, skipping deletion", collectionName);

                // Log skipped deletion
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> auditContext = new HashMap<>();
                auditContext.put("collectionName", collectionName);
                auditContext.put("teamId", teamId);
                auditContext.put("reason", "Collection does not exist");
                auditContext.put("durationMs", durationMs);

                // Chain audit logging before returning
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_DELETED,
                        AuditActionType.DELETE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection deletion skipped - collection '%s' does not exist", collectionName),
                        auditContext,
                        null,
                        null,
                        AuditResultType.SUCCESS)
                        .doOnError(auditError -> log.error("Failed to log audit event (collection does not exist): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .thenReturn(Map.of("message", "Collection " + collectionName + " does not exist"));
            }

            long rowCount = getCollectionRowCount(collectionName);
            if (rowCount > 0) {
                String message = String.format("Collection %s contains %d records and cannot be deleted",
                        collectionName, rowCount);
                log.warn(message);

                // Log denied deletion
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("rowCount", rowCount);
                errorContext.put("error", message);
                errorContext.put("durationMs", durationMs);
                errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_DELETED,
                        AuditActionType.DELETE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection deletion denied for '%s' - contains %d records", collectionName,
                                rowCount),
                        errorContext,
                        null,
                        null,
                        AuditResultType.DENIED)
                        .doOnError(auditError -> log.error("Failed to log audit event (deletion denied): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new IllegalStateException(message)));
            }

            milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build());
            log.info("Deleted collection {}", collectionName);

            // Log successful deletion
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("collectionName", collectionName);
            auditContext.put("teamId", teamId);
            auditContext.put("rowCount", rowCount);
            auditContext.put("durationMs", durationMs);
            auditContext.put("deletionTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_DELETED,
                    AuditActionType.DELETE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Collection '%s' deleted successfully for team '%s'", collectionName, teamId),
                    auditContext,
                    null,
                    null)
                    .doOnError(auditError -> log.error("Failed to log audit event (collection deleted): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .thenReturn(Map.of("message", "Collection " + collectionName + " deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete collection {}: {}", collectionName, e.getMessage());

            // Log deletion failure
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("collectionName", collectionName);
            errorContext.put("teamId", teamId);
            errorContext.put("error", e.getMessage());
            errorContext.put("errorType", e.getClass().getSimpleName());
            errorContext.put("durationMs", durationMs);
            errorContext.put("failureTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning error
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_DELETED,
                    AuditActionType.DELETE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Collection deletion failed for '%s': %s", collectionName, e.getMessage()),
                    errorContext,
                    null,
                    null,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (deletion failed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .then(Mono.error(e));
        }
    }

    @Override
    public Mono<List<MilvusCollectionInfo>> listCollections(String teamId, String collectionType) {
        log.info("Listing collections for team {} with type filter: {}", teamId, collectionType);
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder()

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

                                            .build());

                            // Check if collection has teamId property matching the requested teamId
                            for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                                if ("teamId".equals(property.getKey()) && teamId.equals(property.getValue())) {
                                    MilvusCollectionInfo info = buildCollectionInfo(collectionName, teamId,
                                            describeResponse.getData());
                                    if (collectionType != null && !collectionType.isBlank()) {
                                        String normalizedFilter = collectionType.trim().toLowerCase();
                                        String infoType = info.getCollectionType() != null
                                                ? info.getCollectionType().toLowerCase()
                                                : "";
                                        if (!normalizedFilter.equals(infoType)) {
                                            return null;
                                        }
                                    }
                                    log.info("Found collection {} for team {}", collectionName, teamId);
                                    return info;
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
    public Mono<Map<String, String>> updateCollectionMetadata(String collectionName,
            String teamId,
            Map<String, String> metadata) {
        log.info("Updating metadata for collection {} (team {})", collectionName, teamId);
        LocalDateTime startTime = LocalDateTime.now();

        if (metadata == null || metadata.isEmpty()) {
            // Log skipped update
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("collectionName", collectionName);
            auditContext.put("teamId", teamId);
            auditContext.put("reason", "No metadata provided");
            auditContext.put("durationMs", durationMs);

            // Chain audit logging before returning
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_UPDATED,
                    AuditActionType.UPDATE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Collection metadata update skipped for '%s' - no metadata provided", collectionName),
                    auditContext,
                    null,
                    null,
                    AuditResultType.SUCCESS)
                    .doOnError(auditError -> log.error("Failed to log audit event (no metadata provided): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .thenReturn(Map.of("message", "No metadata provided"));
        }

        try {
            Map<String, String> sanitizedMetadata = new HashMap<>();
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    sanitizedMetadata.put(key.trim(), value != null ? value.trim() : "");
                }
            });

            if (sanitizedMetadata.isEmpty()) {
                // Log skipped update
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> auditContext = new HashMap<>();
                auditContext.put("collectionName", collectionName);
                auditContext.put("teamId", teamId);
                auditContext.put("reason", "No valid metadata after sanitization");
                auditContext.put("durationMs", durationMs);

                // Chain audit logging before returning
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_UPDATED,
                        AuditActionType.UPDATE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection metadata update skipped for '%s' - no valid metadata",
                                collectionName),
                        auditContext,
                        null,
                        null,
                        AuditResultType.SUCCESS)
                        .doOnError(auditError -> log.error("Failed to log audit event (no valid metadata): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .thenReturn(Map.of("message", "No metadata provided"));
            }

            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            if (describeResponse.getStatus() != 0 || describeResponse.getData() == null) {
                // Log collection not found
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("error", "Collection not found: " + collectionName);
                errorContext.put("durationMs", durationMs);

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_UPDATED,
                        AuditActionType.UPDATE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection metadata update failed for '%s' - collection not found",
                                collectionName),
                        errorContext,
                        null,
                        null,
                        AuditResultType.FAILED)
                        .doOnError(auditError -> log.error("Failed to log audit event (collection not found): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new RuntimeException("Collection not found: " + collectionName)));
            }

            boolean teamMatch = describeResponse.getData().getPropertiesList().stream()
                    .anyMatch(property -> "teamId".equals(property.getKey()) && teamId.equals(property.getValue()));

            if (!teamMatch) {
                // Log team mismatch (DENIED)
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("error", "Collection " + collectionName + " does not belong to team " + teamId);
                errorContext.put("durationMs", durationMs);

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_UPDATED,
                        AuditActionType.UPDATE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection metadata update denied for '%s' - team mismatch", collectionName),
                        errorContext,
                        null,
                        null,
                        AuditResultType.DENIED)
                        .doOnError(auditError -> log.error("Failed to log audit event (team mismatch): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new RuntimeException(
                                "Collection " + collectionName + " does not belong to team " + teamId)));
            }

            String currentAlias = describeResponse.getData().getPropertiesList().stream()
                    .filter(property -> "collectionAlias".equals(property.getKey()))
                    .map(KeyValuePair::getValue)
                    .findFirst()
                    .orElse("");

            String requestedAlias = sanitizedMetadata.getOrDefault("collectionAlias", currentAlias);

            boolean aliasChange = sanitizedMetadata.containsKey("collectionAlias")
                    && !Objects.equals(normalizeAlias(currentAlias), normalizeAlias(requestedAlias));

            if (aliasChange && isCollectionNameLocked(collectionName)) {
                // Log denied update due to locked collection
                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                Map<String, Object> errorContext = new HashMap<>();
                errorContext.put("collectionName", collectionName);
                errorContext.put("teamId", teamId);
                errorContext.put("currentAlias", currentAlias);
                errorContext.put("requestedAlias", requestedAlias);
                errorContext.put("error", "Collection " + collectionName
                        + " is referenced by existing agent workflows and its name cannot be changed.");
                errorContext.put("durationMs", durationMs);

                // Chain audit logging before returning error
                return auditLogHelper.logDetailedEvent(
                        AuditEventType.RAG_COLLECTION_UPDATED,
                        AuditActionType.UPDATE,
                        AuditResourceType.COLLECTION,
                        collectionName,
                        String.format("Collection metadata update denied for '%s' - collection name is locked",
                                collectionName),
                        errorContext,
                        null,
                        null,
                        AuditResultType.DENIED)
                        .doOnError(auditError -> log.error("Failed to log audit event (name locked): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                        .then(Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Collection " + collectionName
                                        + " is referenced by existing agent workflows and its name cannot be changed.")));
            }

            if (sanitizedMetadata.containsKey("collectionAlias")) {
                sanitizedMetadata.put("collectionAlias", normalizeAlias(requestedAlias));
            }

            AlterCollectionParam.Builder alterBuilder = AlterCollectionParam.newBuilder()
                    .withCollectionName(collectionName);

            sanitizedMetadata.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    alterBuilder.withProperty(key.trim(), value);
                }
            });

            milvusClient.alterCollection(alterBuilder.build());
            log.info("Updated metadata for collection {}: {}", collectionName, sanitizedMetadata);

            // Log successful update
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("collectionName", collectionName);
            auditContext.put("teamId", teamId);
            auditContext.put("metadata", sanitizedMetadata);
            auditContext.put("metadataKeys", sanitizedMetadata.keySet());
            auditContext.put("durationMs", durationMs);
            auditContext.put("updateTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_UPDATED,
                    AuditActionType.UPDATE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Collection metadata updated successfully for '%s'", collectionName),
                    auditContext,
                    null,
                    null)
                    .doOnError(auditError -> log.error("Failed to log audit event (metadata updated): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .thenReturn(Map.of("message", "Collection metadata updated"));
        } catch (Exception e) {
            log.error("Failed to update collection metadata for {}: {}", collectionName, e.getMessage());

            // Log update failure
            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("collectionName", collectionName);
            errorContext.put("teamId", teamId);
            errorContext.put("metadata", metadata);
            errorContext.put("error", e.getMessage());
            errorContext.put("errorType", e.getClass().getSimpleName());
            errorContext.put("durationMs", durationMs);
            errorContext.put("failureTimestamp", LocalDateTime.now().toString());

            // Chain audit logging before returning error
            return auditLogHelper.logDetailedEvent(
                    AuditEventType.RAG_COLLECTION_UPDATED,
                    AuditActionType.UPDATE,
                    AuditResourceType.COLLECTION,
                    collectionName,
                    String.format("Collection metadata update failed for '%s': %s", collectionName, e.getMessage()),
                    errorContext,
                    null,
                    null,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (metadata update failed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                    .then(Mono.error(e));
        }
    }

    private String normalizeAlias(String alias) {
        if (alias == null) {
            return "";
        }
        return alias.trim();
    }

    @Override
    public Mono<List<MilvusCollectionInfo>> listAllCollections() {
        log.info("Listing all collections");
        try {
            R<ShowCollectionsResponse> response = milvusClient.showCollections(ShowCollectionsParam.newBuilder()

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

                                            .build());

                            // Find teamId in properties
                            for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                                if ("teamId".equals(property.getKey())) {
                                    String teamId = property.getValue();
                                    log.info("Found teamId {} for collection {}", teamId, collectionName);
                                    return buildCollectionInfo(collectionName, teamId, describeResponse.getData());
                                }
                            }

                            log.info("No teamId found for collection {}", collectionName);
                            return buildCollectionInfo(collectionName, "unknown", describeResponse.getData());
                        } catch (Exception e) {
                            log.warn("Failed to get properties for collection {}: {}", collectionName, e.getMessage());
                            return MilvusCollectionInfo.builder()
                                    .name(collectionName)
                                    .teamId("unknown")
                                    .nameLocked(isCollectionNameLocked(collectionName))
                                    .rowCount(null)
                                    .build();
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
    public Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId,
            String target, String modelName) {
        return verifyRecord(collectionName, textField, text, teamId, target, modelName, null);
    }

    @Override
    public Mono<Map<String, Object>> verifyRecord(String collectionName, String textField, String text, String teamId,
            String target, String modelName, Map<String, Object> metadataFilters) {
        log.info(
                "Verifying record in collection {} for team {} with text: {} using target: {} and model: {} with filters: {}",
                collectionName, teamId, text, target, modelName, metadataFilters);
        try {
            // First verify the collection belongs to the team
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            // Check if describeCollection was successful
            if (describeResponse.getStatus() != 0) {
                log.error("Failed to describe collection {}: status={}, message={}",
                        collectionName, describeResponse.getStatus(), describeResponse.getMessage());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("found", false);
                errorResponse.put("message",
                        "Collection does not exist or access denied: " + describeResponse.getMessage());
                errorResponse.put("id", null);
                errorResponse.put(textField, null);
                errorResponse.put("distance", null);
                errorResponse.put("match_type", null);
                errorResponse.put("search_text", text);
                // Add empty metadata fields if any were requested
                if (metadataFilters != null) {
                    for (String fieldName : metadataFilters.keySet()) {
                        errorResponse.put(fieldName, null);
                    }
                }
                return Mono.just(errorResponse);
            }

            if (describeResponse.getData() == null) {
                log.error("Describe collection {} returned null data", collectionName);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("found", false);
                errorResponse.put("message", "Collection does not exist or access denied");
                errorResponse.put("id", null);
                errorResponse.put(textField, null);
                errorResponse.put("distance", null);
                errorResponse.put("match_type", null);
                errorResponse.put("search_text", text);
                // Add empty metadata fields if any were requested
                if (metadataFilters != null) {
                    for (String fieldName : metadataFilters.keySet()) {
                        errorResponse.put(fieldName, null);
                    }
                }
                return Mono.just(errorResponse);
            }

            DescribeCollectionResponse collectionData = describeResponse.getData();

            // Log collection schema for debugging
            // log.info("Collection schema for {}: {}", collectionName,
            // collectionData.getSchema());

            boolean teamIdMatches = false;
            for (KeyValuePair property : collectionData.getPropertiesList()) {
                if ("teamId".equals(property.getKey()) && teamId.equals(property.getValue())) {
                    teamIdMatches = true;
                    break;
                }
            }

            if (!teamIdMatches) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("found", false);
                errorResponse.put("message", "Collection does not belong to team " + teamId);
                errorResponse.put("id", null);
                errorResponse.put(textField, null);
                errorResponse.put("distance", null);
                errorResponse.put("match_type", null);
                errorResponse.put("search_text", text);
                // Add empty metadata fields if any were requested
                if (metadataFilters != null) {
                    for (String fieldName : metadataFilters.keySet()) {
                        errorResponse.put(fieldName, null);
                    }
                }
                return Mono.just(errorResponse);
            }

            // Get embedding for the search text using dynamic llm target and model
            return getEmbedding(text, target, modelName, teamId)
                    .flatMap(searchEmbedding -> {
                        try {
                            // Build dynamic out fields and get available fields list
                            List<String> availableFields = collectionData.getSchema().getFieldsList()
                                    .stream()
                                    .map(io.milvus.grpc.FieldSchema::getName)
                                    .collect(Collectors.toList());

                            // Build dynamic filter expression (pass available fields to check schema)
                            String filterExpression = buildFilterExpression(teamId, metadataFilters, textField,
                                    availableFields);

                            // Build dynamic out fields (availableFields already defined above)

                            String collectionType = collectionData.getPropertiesList().stream()
                                    .filter(property -> "collectionType".equals(property.getKey()))
                                    .map(KeyValuePair::getValue)
                                    .findFirst()
                                    .orElse(null);

                            List<String> rawOutFields = buildOutFields(textField, metadataFilters, availableFields,
                                    collectionType);
                            // Ensure decryption fields are included and variable is final for lambda use
                            final List<String> outFields = ensureDecryptionFields(rawOutFields, availableFields);

                            // Use vector search to find similar records
                            SearchParam.Builder searchParamBuilder = SearchParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .withFloatVectors(List.of(searchEmbedding))
                                    .withLimit(5L)
                                    .withMetricType(METRIC_TYPE)
                                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                                    .withVectorFieldName(EMBEDDING_FIELD)
                                    .withOutFields(outFields)
                                    .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}");

                            // Only add filter expression if it's not empty
                            if (!filterExpression.isEmpty()) {
                                searchParamBuilder.withExpr(filterExpression);
                            }

                            SearchParam searchParam = searchParamBuilder.build();

                            log.info("Executing semantic search for text: {} with filter: {}", text, filterExpression);
                            SearchResults results = milvusClient.search(searchParam).getData();

                            if (results == null || results.getResults().getNumQueries() == 0) {
                                log.info("No results found in semantic search");
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

                            // Use Flux to process results reactively
                            return Flux.range(0, (int) resultData.getNumQueries())
                                    .concatMap(i -> {
                                        // Extract ID
                                        Object id = null;
                                        var idFieldData = resultData.getFieldsDataList().stream()
                                                .filter(f -> f.getFieldName().equals("id"))
                                                .findFirst()
                                                .orElse(null);
                                        if (idFieldData != null && idFieldData.getScalars().hasLongData()
                                                && idFieldData.getScalars().getLongData().getDataCount() > i) {
                                            id = idFieldData.getScalars().getLongData().getData(i);
                                        }

                                        final Object fId = id;

                                        // Extract Distance
                                        Float distance = 0.0f;
                                        var distanceField = resultData.getFieldsDataList().stream()
                                                .filter(f -> f.getFieldName().equals("distance"))
                                                .findFirst()
                                                .orElse(null);

                                        if (distanceField != null) {
                                            if (distanceField.getScalars().hasFloatData()
                                                    && distanceField.getScalars().getFloatData().getDataCount() > i) {
                                                distance = distanceField.getScalars().getFloatData().getData(i);
                                            } else if (distanceField.getScalars().hasDoubleData()
                                                    && distanceField.getScalars().getDoubleData().getDataCount() > i) {
                                                distance = (float) distanceField.getScalars().getDoubleData()
                                                        .getData(i);
                                            }
                                        }
                                        final Float fDistance = distance;

                                        // Extract metadata
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

                                        // Decrypt text reactively
                                        return decryptTextFromSearchResults(resultData, textField, i, teamId)
                                                .map(decryptedText -> {
                                                    Map<String, Object> doc = new HashMap<>();
                                                    doc.put("id", fId);
                                                    doc.put("text", decryptedText);
                                                    doc.put("distance", fDistance);
                                                    doc.put("metadata", metadata);
                                                    return doc;
                                                });
                                    })
                                    .collectList()
                                    .flatMap(documents -> {
                                        // Find exact match
                                        for (Map<String, Object> doc : documents) {
                                            String storedText = (String) doc.get("text");
                                            if (storedText != null && storedText.equalsIgnoreCase(text)) {
                                                Map<String, Object> match = new HashMap<>();
                                                match.put("found", true);
                                                match.put("id", doc.get("id"));
                                                match.put(textField, storedText);
                                                match.put("distance", doc.get("distance"));
                                                match.put("match_type", "exact");
                                                match.put("search_text", text);
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> metadataMap = (Map<String, Object>) doc
                                                        .get("metadata");
                                                match.putAll(metadataMap);
                                                return Mono.just(match);
                                            }
                                        }

                                        // If no exact match, return best semantic match
                                        if (!documents.isEmpty()) {
                                            Map<String, Object> bestDoc = documents.get(0);
                                            Map<String, Object> bestMatch = new HashMap<>();
                                            bestMatch.put("found", true);
                                            bestMatch.put("id", bestDoc.get("id"));
                                            bestMatch.put(textField, bestDoc.get("text"));
                                            bestMatch.put("distance", bestDoc.get("distance"));

                                            float dist = (Float) bestDoc.get("distance");
                                            if (dist < 0.1) {
                                                bestMatch.put("match_type", "exact_semantic");
                                            } else if (dist < 0.3) {
                                                bestMatch.put("match_type", "high_similarity");
                                            } else if (dist < 0.5) {
                                                bestMatch.put("match_type", "medium_similarity");
                                            } else {
                                                bestMatch.put("match_type", "low_similarity");
                                            }

                                            bestMatch.put("search_text", text);
                                            bestMatch.put("message",
                                                    "Exact match not found, returning best semantic match");
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> bestMetadataMap = (Map<String, Object>) bestDoc
                                                    .get("metadata");
                                            bestMatch.putAll(bestMetadataMap);
                                            return Mono.just(bestMatch);
                                        }

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
                                    });

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
    private String buildFilterExpression(String teamId, Map<String, Object> metadataFilters,
            Collection<String> availableFields) {
        List<String> conditions = new ArrayList<>();

        // Filter by teamId only if the field exists in the collection schema
        if (availableFields != null && availableFields.contains("teamId") && teamId != null && !teamId.isEmpty()) {
            conditions.add("teamId == \"" + teamId + "\"");
        }

        // Add dynamic metadata filters
        if (metadataFilters != null) {
            for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
                String fieldName = filter.getKey();
                Object value = filter.getValue();

                // Only add filter if the field exists in the schema
                if (value != null && (availableFields == null || availableFields.contains(fieldName))) {
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
     * Build dynamic filter expression for metadata filtering with text field
     * validation
     */
    private String buildFilterExpression(String teamId, Map<String, Object> metadataFilters, String textField,
            Collection<String> availableFields) {
        List<String> conditions = new ArrayList<>();

        // Filter by teamId only if the field exists in the collection schema
        if (availableFields != null && availableFields.contains("teamId") && teamId != null && !teamId.isEmpty()) {
            conditions.add("teamId == \"" + teamId + "\"");
        }

        // Add filter to exclude empty or null text fields (only if textField exists)
        if (textField != null && (availableFields == null || availableFields.contains(textField))) {
            conditions.add(textField + " != \"\"");
            conditions.add(textField + " != null");
        }

        // Add dynamic metadata filters
        if (metadataFilters != null) {
            for (Map.Entry<String, Object> filter : metadataFilters.entrySet()) {
                String fieldName = filter.getKey();
                Object value = filter.getValue();

                // Only add filter if the field exists in the schema
                if (value != null && (availableFields == null || availableFields.contains(fieldName))) {
                    switch (value) {
                        case String s -> conditions.add(fieldName + " == \"" + value + "\"");
                        case Number number -> conditions.add(fieldName + " == " + value);
                        case Boolean b -> conditions.add(fieldName + " == " + value);
                        default -> {
                        }
                    }
                }
            }
        }

        return conditions.isEmpty() ? "" : String.join(" && ", conditions);
    }

    /**
     * Build dynamic out fields list
     */
    private List<String> buildOutFields(String textField,
            Map<String, Object> metadataFilters,
            Collection<String> availableFields,
            String collectionType) {
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        requested.add("id");
        if (StringUtils.hasText(textField)) {
            requested.add(textField);
        }

        boolean isKnowledgeHub = StringUtils.hasText(collectionType)
                && "KNOWLEDGE_HUB".equalsIgnoreCase(collectionType);
        if (isKnowledgeHub) {
            KNOWLEDGE_HUB_DEFAULT_OUT_FIELDS.forEach(requested::add);
        }

        if (metadataFilters != null) {
            requested.addAll(metadataFilters.keySet());
        }

        if (availableFields != null && !availableFields.isEmpty()) {
            Set<String> allowed = availableFields instanceof Set ? (Set<String>) availableFields
                    : new HashSet<>(availableFields);
            requested.retainAll(allowed);
            if (!isKnowledgeHub) {
                // For custom collections, include all schema fields (minus embedding/vector
                // field) to surface custom metadata.
                for (String fieldName : allowed) {
                    if (!EMBEDDING_FIELD.equals(fieldName)) {
                        requested.add(fieldName);
                    }
                }
            }
        }

        return new ArrayList<>(requested);
    }

    /**
     * Extract field value based on data type
     */
    private Object extractFieldValue(FieldData fieldData, int index) {
        if (fieldData.getScalars().hasStringData() && fieldData.getScalars().getStringData().getDataCount() > index) {
            return fieldData.getScalars().getStringData().getData(index);
        } else if (fieldData.getScalars().hasLongData()
                && fieldData.getScalars().getLongData().getDataCount() > index) {
            return fieldData.getScalars().getLongData().getData(index);
        } else if (fieldData.getScalars().hasFloatData()
                && fieldData.getScalars().getFloatData().getDataCount() > index) {
            return fieldData.getScalars().getFloatData().getData(index);
        } else if (fieldData.getScalars().hasDoubleData()
                && fieldData.getScalars().getDoubleData().getDataCount() > index) {
            return fieldData.getScalars().getDoubleData().getData(index);
        } else if (fieldData.getScalars().hasBoolData()
                && fieldData.getScalars().getBoolData().getDataCount() > index) {
            return fieldData.getScalars().getBoolData().getData(index);
        }
        return null;
    }

    @Override
    public Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId,
            String target, String modelName) {
        return searchRecord(collectionName, textField, text, teamId, target, modelName, 10, null);
    }

    @Override
    public Mono<Map<String, Object>> searchRecord(String collectionName, String textField, String text, String teamId,
            String target, String modelName, int nResults, Map<String, Object> metadataFilters) {
        log.info(
                "Searching records in collection {} for team {} with text: {} using target: {} and model: {} with filters: {} and nResults: {}",
                collectionName, teamId, text, target, modelName, metadataFilters, nResults);
        try {
            // First verify the collection belongs to the team
            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(
                    DescribeCollectionParam.newBuilder()
                            .withCollectionName(collectionName)

                            .build());

            // Check if describeCollection was successful
            if (describeResponse.getStatus() != 0) {
                log.error("Failed to describe collection {}: status={}, message={}",
                        collectionName, describeResponse.getStatus(), describeResponse.getMessage());
                return Mono.just(Map.of(
                        "found", false,
                        "message", "Collection does not exist or access denied: " + describeResponse.getMessage(),
                        "search_text", text,
                        "total_results", 0,
                        "results", new ArrayList<>()));
            }

            if (describeResponse.getData() == null) {
                log.error("Describe collection {} returned null data", collectionName);
                return Mono.just(Map.of(
                        "found", false,
                        "message", "Collection does not exist or access denied",
                        "search_text", text,
                        "total_results", 0,
                        "results", new ArrayList<>()));
            }

            // Log collection schema for debugging
            // log.info("Collection schema for {}: {}", collectionName,
            // describeResponse.getData().getSchema());
            // log.info("Available fields:");
            // for (io.milvus.grpc.FieldSchema field :
            // describeResponse.getData().getSchema().getFieldsList()) {
            // log.info(" - {}: {} (primary: {})", field.getName(), field.getDataType(),
            // field.getIsPrimaryKey());
            // }

            boolean teamIdMatches = false;
            for (KeyValuePair property : describeResponse.getData().getPropertiesList()) {
                if ("teamId".equals(property.getKey()) && teamId.equals(property.getValue())) {
                    teamIdMatches = true;
                    break;
                }
            }

            if (!teamIdMatches) {
                return Mono.just(Map.of(
                        "found", false,
                        "message", "Collection does not belong to team " + teamId,
                        "search_text", text,
                        "total_results", 0,
                        "results", new ArrayList<>()));
            }

            // Get embedding for the search text using dynamic tool and model
            return getEmbedding(text, target, modelName, teamId)
                    .flatMap(searchEmbedding -> {
                        try {
                            // Build dynamic out fields and get available fields list
                            List<String> availableFields = describeResponse.getData().getSchema().getFieldsList()
                                    .stream()
                                    .map(io.milvus.grpc.FieldSchema::getName)
                                    .collect(Collectors.toList());

                            // Build dynamic filter expression (pass available fields to check schema)
                            String filterExpression = buildFilterExpression(teamId, metadataFilters, availableFields);

                            String collectionType = describeResponse.getData().getPropertiesList().stream()
                                    .filter(property -> "collectionType".equals(property.getKey()))
                                    .map(KeyValuePair::getValue)
                                    .findFirst()
                                    .orElse(null);

                            List<String> outFields = buildOutFields(textField, metadataFilters, availableFields,
                                    collectionType);
                            // Ensure decryption fields are included (only if they exist in the collection)
                            outFields = ensureDecryptionFields(outFields, availableFields);

                            // Use vector search to find similar records
                            SearchParam.Builder searchParamBuilder = SearchParam.newBuilder()
                                    .withCollectionName(collectionName)
                                    .withFloatVectors(List.of(searchEmbedding))
                                    .withLimit((long) nResults)
                                    .withMetricType(METRIC_TYPE)
                                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                                    .withVectorFieldName(EMBEDDING_FIELD)
                                    .withOutFields(outFields)
                                    .withParams("{\"ef\":" + SEARCH_PARAM_EF + "}");

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

                            // Process search results reactively
                            SearchResultData resultData = results.getResults();
                            List<Long> topKs = resultData.getTopksList();

                            final List<String> finalOutFields = outFields;

                            return Flux.range(0, topKs.size())
                                    .concatMap(queryIndex -> {
                                        int resultsForQuery = Math.toIntExact(topKs.get(queryIndex));
                                        int currentOffset = queryIndex * resultsForQuery; // simplified offset
                                                                                          // calculation assuming equal
                                                                                          // nResults

                                        return Flux.range(0, resultsForQuery)
                                                .concatMap(rank -> {
                                                    int index = currentOffset + rank;
                                                    Map<String, Object> record = new HashMap<>();

                                                    // Add query/rank context
                                                    record.put("query_index", queryIndex);
                                                    record.put("rank", rank + 1);

                                                    // Primary key / ID field
                                                    if (resultData.getIds().hasIntId()
                                                            && resultData.getIds().getIntId().getDataCount() > index) {
                                                        record.put("id", resultData.getIds().getIntId().getData(index));
                                                    } else if (resultData.getIds().hasStrId()
                                                            && resultData.getIds().getStrId().getDataCount() > index) {
                                                        record.put("id", resultData.getIds().getStrId().getData(index));
                                                    }

                                                    // Distance (similarity score)
                                                    float distance = 0.0f;
                                                    if (resultData.getScoresCount() > index) {
                                                        distance = resultData.getScores(index);
                                                    }
                                                    record.put("distance", distance);

                                                    if (distance < 0.1f) {
                                                        record.put("match_type", "exact");
                                                    } else if (distance < 0.3f) {
                                                        record.put("match_type", "high_similarity");
                                                    } else if (distance < 0.5f) {
                                                        record.put("match_type", "medium_similarity");
                                                    } else {
                                                        record.put("match_type", "low_similarity");
                                                    }

                                                    // Additional metadata fields
                                                    for (String fieldName : finalOutFields) {
                                                        if (!fieldName.equals("id") && !fieldName.equals(textField)) {
                                                            var fieldData = resultData.getFieldsDataList().stream()
                                                                    .filter(f -> f.getFieldName().equals(fieldName))
                                                                    .findFirst()
                                                                    .orElse(null);
                                                            if (fieldData != null) {
                                                                Object value = extractFieldValue(fieldData, index);
                                                                if (value != null) {
                                                                    record.put(fieldName, value);
                                                                }
                                                            }
                                                        }
                                                    }

                                                    final Map<String, Object> fRecord = record;

                                                    // Chunk text field (decrypted)
                                                    return decryptTextFromSearchResults(resultData, textField, index,
                                                            teamId)
                                                            .map(decryptedText -> {
                                                                if (decryptedText != null && !decryptedText.isEmpty()) {
                                                                    fRecord.put(textField, decryptedText);
                                                                }
                                                                return fRecord;
                                                            });
                                                });
                                    })
                                    .collectList()
                                    .flatMap(searchResults -> {
                                        // Build the final response
                                        Map<String, Object> searchResponse = new HashMap<>();
                                        searchResponse.put("found", true);
                                        searchResponse.put("search_text", text);
                                        searchResponse.put("total_results", searchResults.size());
                                        searchResponse.put("results", searchResults);
                                        searchResponse.put("message",
                                                String.format("Found %d relevant records", searchResults.size()));

                                        log.info("Successfully searched records with {} results", searchResults.size());
                                        return Mono.just(searchResponse);
                                    });
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
                R<ShowCollectionsResponse> fallbackResponse = milvusClient
                        .showCollections(ShowCollectionsParam.newBuilder()
                                .build());

                if (fallbackResponse.getStatus() != 0) {
                    String fallbackErrorMessage = fallbackResponse.getMessage();
                    if (fallbackErrorMessage == null) {
                        fallbackErrorMessage = "Unknown error occurred while listing collections";
                    }
                    log.error("Fallback ShowCollections also failed with status {}: {}", fallbackResponse.getStatus(),
                            fallbackErrorMessage);
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

                                    .build());

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

                                        .build());
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

    @Override
    public Mono<MilvusCollectionVerificationResponse> verifyCollection(String collectionName, String teamId) {
        log.info("Verifying Milvus collection {} for team {}", collectionName, teamId);
        try {
            DescribeCollectionParam describeParam = DescribeCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build();

            R<DescribeCollectionResponse> describeResponse = milvusClient.describeCollection(describeParam);
            if (describeResponse.getStatus() != 0 || describeResponse.getData() == null) {
                String message = describeResponse.getMessage() != null ? describeResponse.getMessage()
                        : "Unknown error";
                log.error("Failed to describe collection {}: {}", collectionName, message);
                return Mono.error(new RuntimeException("Failed to describe Milvus collection: " + message));
            }

            DescribeCollectionResponse data = describeResponse.getData();

            String collectionTeamId = null;
            Map<String, String> collectionProperties = new LinkedHashMap<>();
            for (KeyValuePair property : data.getPropertiesList()) {
                collectionProperties.put(property.getKey(), property.getValue());
                if ("teamId".equals(property.getKey())) {
                    collectionTeamId = property.getValue();
                }
            }

            if (collectionTeamId != null && !Objects.equals(collectionTeamId, teamId)) {
                return Mono.error(new RuntimeException(
                        "Milvus collection '" + collectionName + "' does not belong to team " + teamId));
            }

            List<Map<String, Object>> schemaFields = new ArrayList<>();
            for (FieldSchema field : data.getSchema().getFieldsList()) {
                Map<String, Object> fieldInfo = new HashMap<>();
                fieldInfo.put("name", field.getName());
                fieldInfo.put("dataType", field.getDataType().name());
                fieldInfo.put("isPrimary", field.getIsPrimaryKey());

                if (field.getTypeParamsCount() > 0) {
                    Map<String, String> typeParams = new HashMap<>();
                    for (KeyValuePair param : field.getTypeParamsList()) {
                        typeParams.put(param.getKey(), param.getValue());
                    }
                    fieldInfo.put("typeParams", typeParams);
                }

                schemaFields.add(fieldInfo);
            }

            long rowCount = 0L;
            try {
                GetCollectionStatisticsParam statsParam = GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collectionName)

                        .build();
                R<GetCollectionStatisticsResponse> statsResponse = milvusClient.getCollectionStatistics(statsParam);
                if (statsResponse.getData() != null) {
                    for (KeyValuePair stat : statsResponse.getData().getStatsList()) {
                        if ("row_count".equals(stat.getKey())) {
                            rowCount = Long.parseLong(stat.getValue());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch row count for collection {}: {}", collectionName, e.getMessage());
            }

            MilvusSchemaValidator.ValidationResult validationResult = MilvusSchemaValidator.validate(schemaFields,
                    null);

            List<MilvusCollectionVerificationResponse.FieldInfo> fieldInfos = schemaFields.stream()
                    .map(field -> {
                        Map<String, String> typeParams = null;
                        Object paramsObj = field.get("typeParams");
                        if (paramsObj instanceof Map<?, ?> map) {
                            typeParams = map.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            entry -> String.valueOf(entry.getKey()),
                                            entry -> String.valueOf(entry.getValue())));
                        }

                        Integer maxLength = null;
                        if (typeParams != null && typeParams.containsKey("max_length")) {
                            try {
                                maxLength = Integer.parseInt(typeParams.get("max_length"));
                            } catch (NumberFormatException ignored) {
                                // ignore invalid value
                            }
                        }

                        return MilvusCollectionVerificationResponse.FieldInfo.builder()
                                .name(String.valueOf(field.get("name")))
                                .dataType(String.valueOf(field.get("dataType")))
                                .primary(Boolean.TRUE.equals(field.get("isPrimary")))
                                .typeParams(typeParams)
                                .maxLength(maxLength)
                                .build();
                    })
                    .collect(Collectors.toList());

            MilvusCollectionVerificationResponse response = MilvusCollectionVerificationResponse.builder()
                    .name(collectionName)
                    .teamId(collectionTeamId != null ? collectionTeamId : teamId)
                    .vectorDimension(validationResult.getVectorDimension())
                    .vectorFieldName(validationResult.getVectorFieldName())
                    .rowCount(rowCount)
                    .description(data.getSchema() != null ? data.getSchema().getDescription() : null)
                    .valid(validationResult.isValid())
                    .issues(validationResult.getIssues())
                    .properties(collectionProperties)
                    .schema(fieldInfos)
                    .build();

            return Mono.just(response);
        } catch (Exception e) {
            log.error("Failed to verify Milvus collection {}: {}", collectionName, e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Long> deleteDocumentEmbeddings(String collectionName, String documentId, String teamId) {
        log.info("Deleting embeddings for document {} in collection {} (team {})", documentId, collectionName, teamId);

        try {
            // Check if collection exists first to avoid "collection not found" error
            R<Boolean> hasCollection = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)

                    .build());

            if (hasCollection.getData() == null || !hasCollection.getData()) {
                log.warn("Collection {} does not exist, skipping deletion of document {}", collectionName, documentId);
                return Mono.just(0L);
            }

            String expr = String.format("documentId == \"%s\" && teamId == \"%s\"", documentId, teamId);

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)

                    .withExpr(expr)
                    .build();

            R<MutationResult> response = milvusClient.delete(deleteParam);

            if (response.getStatus() != 0) {
                String message = response.getMessage() != null ? response.getMessage() : "Unknown error";
                log.error("Milvus delete operation failed for collection {}: {}", collectionName, message);
                return Mono.error(new RuntimeException("Failed to delete embeddings from Milvus: " + message));
            }

            MutationResultWrapper wrapper = new MutationResultWrapper(response.getData());
            long deletedCount = wrapper.getDeleteCount();
            log.info("Deleted {} embeddings for document {} in collection {}", deletedCount, documentId,
                    collectionName);
            return Mono.just(deletedCount);
        } catch (Exception e) {
            log.error("Failed to delete embeddings for document {} in collection {}: {}", documentId, collectionName,
                    e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    public Mono<Long> countDocumentEmbeddings(String collectionName, String documentId, String teamId) {
        log.info("Counting embeddings for document {} in collection {} (team {})", documentId, collectionName, teamId);
        try {
            String expr = String.format("documentId == \"%s\" && teamId == \"%s\"", documentId, teamId);

            QueryParam.Builder queryBuilder = QueryParam.newBuilder()
                    .withCollectionName(collectionName)

                    .withExpr(expr);
            queryBuilder.withOutFields(Collections.singletonList("documentId"));
            QueryParam queryParam = queryBuilder.build();

            R<QueryResults> response = milvusClient.query(queryParam);
            if (response.getStatus() != 0) {
                String message = response.getMessage() != null ? response.getMessage() : "Unknown error";
                log.error("Milvus query operation failed for collection {}: {}", collectionName, message);
                return Mono.error(new RuntimeException("Failed to query embeddings from Milvus: " + message));
            }

            QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
            long count = wrapper.getRowCount();
            log.info("Counted {} embeddings for document {} in collection {}", count, documentId, collectionName);
            return Mono.just(count);
        } catch (Exception e) {
            log.error("Failed to count embeddings for document {} in collection {}: {}", documentId, collectionName,
                    e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * Helper method to decrypt text from Milvus search results.
     * Extracts encryptionKeyVersion and teamId from field data and decrypts the
     * text.
     * 
     * @param resultData    The search result data
     * @param textField     The name of the text field
     * @param index         The index of the result to decrypt
     * @param defaultTeamId The default teamId to use if not found in results
     * @return Decrypted text, or encrypted text if decryption fails
     */
    private Mono<String> decryptTextFromSearchResults(SearchResultData resultData, String textField, int index,
            String defaultTeamId) {
        // Get encrypted text
        var textFieldData = resultData.getFieldsDataList().stream()
                .filter(f -> f.getFieldName().equals(textField))
                .findFirst()
                .orElse(null);

        String encryptedText = null;
        if (textFieldData != null) {
            Object value = extractFieldValue(textFieldData, index);
            if (value != null) {
                encryptedText = value.toString();
            }
        }

        if (encryptedText == null || encryptedText.isEmpty()) {
            return Mono.just("");
        }

        // Get encryptionKeyVersion - if null/empty, data is unencrypted
        String keyVersion = null;
        var keyVersionFieldData = resultData.getFieldsDataList().stream()
                .filter(f -> f.getFieldName().equals("encryptionKeyVersion"))
                .findFirst()
                .orElse(null);
        if (keyVersionFieldData != null) {
            Object versionObj = extractFieldValue(keyVersionFieldData, index);
            if (versionObj != null) {
                String version = versionObj.toString();
                if (version != null && !version.isEmpty()) {
                    keyVersion = version;
                }
            }
        }

        // Get teamId
        String recordTeamId = defaultTeamId;
        var teamIdFieldData = resultData.getFieldsDataList().stream()
                .filter(f -> f.getFieldName().equals("teamId"))
                .findFirst()
                .orElse(null);
        if (teamIdFieldData != null) {
            Object teamIdObj = extractFieldValue(teamIdFieldData, index);
            if (teamIdObj != null) {
                String tid = teamIdObj.toString();
                if (tid != null && !tid.isEmpty()) {
                    recordTeamId = tid;
                }
            }
        }

        final String fEncryptedText = encryptedText;
        final String fTeamId = recordTeamId;

        // Debug logging for decryption diagnostics
        log.info("🔐 Decryption attempt: teamId={}, keyVersion={}, textLength={}, textPreview={}",
                recordTeamId, keyVersion,
                encryptedText != null ? encryptedText.length() : 0,
                encryptedText != null && encryptedText.length() > 50 ? encryptedText.substring(0, 50) + "..."
                        : encryptedText);

        Mono<String> decryptionMono;

        // If no encryption key version stored, fall back to v1 (legacy data)
        // New collections with encryptionKeyVersion field will have the version stored
        if (keyVersion == null || keyVersion.isEmpty()) {
            // Legacy data: fall back to v1 (HMAC-derived key)
            String legacyVersion = "v1";
            log.info("🔐 No keyVersion stored in collection, using legacy v1 for team {}", fTeamId);
            decryptionMono = chunkEncryptionService.decryptChunkText(fEncryptedText, fTeamId, legacyVersion)
                    .onErrorResume(e -> {
                        log.warn("🔐 Legacy v1 decryption failed for team {}: {}. Returning encrypted text.",
                                fTeamId, e.getMessage());
                        return Mono.just(fEncryptedText);
                    });
        } else {
            log.info("🔐 Using stored keyVersion: {} for team {}", keyVersion, fTeamId);
            decryptionMono = chunkEncryptionService.decryptChunkText(fEncryptedText, fTeamId, keyVersion);
        }

        final String fVersion = keyVersion;

        return decryptionMono
                .doOnNext(decryptedText -> {
                    if (decryptedText != null && decryptedText.equals(fEncryptedText)) {
                        log.warn("🔐 Decrypted text matches encrypted text - decryption may have failed silently");
                    } else {
                        log.info("🔐 Decryption successful, decrypted length: {}",
                                decryptedText != null ? decryptedText.length() : 0);
                    }
                })
                .onErrorResume(e -> {
                    log.warn(
                            "Failed to decrypt chunk text for team {} with key version {}: {}. Returning encrypted text.",
                            fTeamId, fVersion, e.getMessage());
                    return Mono.just(fEncryptedText); // Fallback to encrypted text if decryption fails
                });
    }

    /**
     * Helper method to ensure encryptionKeyVersion and teamId are included in
     * output fields for decryption.
     * Only adds fields if they exist in the collection schema.
     */
    private List<String> ensureDecryptionFields(List<String> outFields, Collection<String> availableFields) {
        List<String> fields = new ArrayList<>(outFields);
        if (availableFields != null) {
            // Only add fields that exist in the collection schema
            if (availableFields.contains("encryptionKeyVersion") && !fields.contains("encryptionKeyVersion")) {
                fields.add("encryptionKeyVersion");
            }
            if (availableFields.contains("teamId") && !fields.contains("teamId")) {
                fields.add("teamId");
            }
        }
        return fields;
    }
}
