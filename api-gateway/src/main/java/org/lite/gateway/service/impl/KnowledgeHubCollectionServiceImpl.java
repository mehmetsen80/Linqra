package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AssignMilvusCollectionRequest;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.KnowledgeHubCollectionService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.validation.validator.MilvusSchemaValidator;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubCollectionServiceImpl implements KnowledgeHubCollectionService {
    
    private static final String DEFAULT_TEXT_FIELD = "text";
    private static final int DEFAULT_SEARCH_RESULTS = 5;
    private static final int MAX_SEARCH_RESULTS = 50;

    private final KnowledgeHubCollectionRepository collectionRepository;
    private final TeamRepository teamRepository;
    private final KnowledgeHubDocumentRepository documentRepository;
    private final LinqMilvusStoreService milvusStoreService;

    @Override
    public Mono<KnowledgeHubCollection> createCollection(String name, String description, List<KnowledgeCategory> categories, String teamId, String createdBy) {
        log.info("Creating knowledge collection: {} for team: {} by user: {}", name, teamId, createdBy);
        
        // Validate team exists
        return teamRepository.existsById(teamId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new RuntimeException("Team not found: " + teamId));
                    }
                    
                    // Check if collection with same name already exists for this team
                    return collectionRepository.findByTeamIdAndName(teamId, name)
                            .hasElement()
                            .flatMap(hasDuplicate -> {
                                if (hasDuplicate) {
                                    return Mono.error(new RuntimeException("Collection with name '" + name + "' already exists for this team"));
                                }
                                
                                // Create new collection
                                KnowledgeHubCollection collection = KnowledgeHubCollection.builder()
                                        .name(name)
                                        .description(description)
                                        .categories(categories)
                                        .teamId(teamId)
                                        .createdBy(createdBy)
                                        .updatedBy(createdBy)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                
                                return collectionRepository.save(collection)
                                        .doOnSuccess(col -> log.info("Created knowledge collection: {} with ID: {}", col.getName(), col.getId()));
                            });
                });
    }
    
    @Override
    public Mono<KnowledgeHubCollection> updateCollection(String id, String name, String description, List<KnowledgeCategory> categories, String teamId, String updatedBy) {
        log.info("Updating knowledge collection: {} for team: {} by user: {}", id, teamId, updatedBy);
        
        return collectionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found: " + id)))
                .flatMap(collection -> {
                    // Verify team ownership
                    if (!collection.getTeamId().equals(teamId)) {
                        return Mono.error(new RuntimeException("Collection not found or access denied: " + id));
                    }
                    
                    // Check if another collection with same name exists (excluding current one)
                    return collectionRepository.findByTeamIdAndName(teamId, name)
                            .filter(existing -> !existing.getId().equals(id))
                            .hasElement()
                            .flatMap(hasDuplicate -> {
                                if (hasDuplicate) {
                                    return Mono.error(new RuntimeException("Collection with name '" + name + "' already exists for this team"));
                                }
                                
                                // Update collection
                                collection.setName(name);
                                collection.setDescription(description);
                                collection.setCategories(categories);
                                collection.setUpdatedBy(updatedBy);
                                collection.setUpdatedAt(LocalDateTime.now());
                                
                                return collectionRepository.save(collection)
                                        .doOnSuccess(col -> log.info("Updated knowledge collection: {} with ID: {}", col.getName(), col.getId()));
                            });
                });
    }
    
    @Override
    public Mono<Void> deleteCollection(String id, String teamId) {
        log.info("Deleting knowledge collection: {} for team: {}", id, teamId);
        
        return collectionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found: " + id)))
                .flatMap(collection -> {
                    // Verify team ownership
                    if (!collection.getTeamId().equals(teamId)) {
                        return Mono.error(new RuntimeException("Collection not found or access denied: " + id));
                    }
                    
                    // Check if collection has any documents
                    return documentRepository.findByCollectionId(id)
                            .count()
                            .flatMap(documentCount -> {
                                if (documentCount > 0) {
                                    return Mono.error(new RuntimeException(
                                        "Cannot delete collection: Collection contains " + documentCount + " document(s). Please delete or move all documents before deleting the collection."
                                    ));
                                }
                                
                                // No documents, safe to delete
                                return collectionRepository.deleteById(id)
                                        .doOnSuccess(v -> log.info("Deleted knowledge collection: {}", id));
                            });
                });
    }
    
    @Override
    public Flux<KnowledgeHubCollection> getCollectionsByTeam(String teamId) {
        log.info("Getting knowledge collections for team: {}", teamId);
        
        return collectionRepository.findByTeamId(Sort.by(Sort.Direction.ASC, "name"), teamId)
                .doOnComplete(() -> log.info("Retrieved knowledge collections for team: {}", teamId));
    }
    
    @Override
    public Mono<KnowledgeHubCollection> getCollectionById(String id, String teamId) {
        log.info("Getting knowledge collection: {} for team: {}", id, teamId);
        
        return collectionRepository.findByIdAndTeamId(id, teamId)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found or access denied: " + id)));
    }
    
    @Override
    public Mono<Long> getCollectionCountByTeam(String teamId) {
        return collectionRepository.countByTeamId(teamId);
    }

    @Override
    public Mono<KnowledgeHubCollection> assignMilvusCollection(String id, String teamId, String updatedBy, AssignMilvusCollectionRequest request) {
        log.info("Assigning Milvus collection {} to KnowledgeHub collection {} for team {}", request.getMilvusCollectionName(), id, teamId);

        return collectionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found: " + id)))
                .flatMap(collection -> {
                    if (!collection.getTeamId().equals(teamId)) {
                        return Mono.error(new RuntimeException("Collection not found or access denied: " + id));
                    }

                    return requireMilvusCollectionForTeam(request.getMilvusCollectionName(), teamId)
                            .flatMap(collectionInfo -> {
                                Integer milvusDimension = collectionInfo.getVectorDimension();
                                Integer requestedDimension = request.getEmbeddingDimension();

                                String collectionType = collectionInfo.getCollectionType();
                                if (collectionType != null && !collectionType.equalsIgnoreCase("KNOWLEDGE_HUB")) {
                                    return Mono.error(new RuntimeException(
                                            "Milvus collection '" + request.getMilvusCollectionName() + "' is not registered as a Knowledge Hub collection."
                                    ));
                                }

                                if (milvusDimension != null && requestedDimension != null && !Objects.equals(milvusDimension, requestedDimension)) {
                                    return Mono.error(new RuntimeException(
                                            "Milvus collection '" + request.getMilvusCollectionName() + "' has vector dimension " + milvusDimension +
                                                    " but embedding model requires " + requestedDimension));
                                }

                                Integer effectiveDimension = milvusDimension != null ? milvusDimension : requestedDimension;
                                if (effectiveDimension == null) {
                                    return Mono.error(new RuntimeException(
                                            "Unable to determine embedding dimension for Milvus collection '" + request.getMilvusCollectionName() + "'."));
                                }

                                return validateMilvusCollectionSchema(request.getMilvusCollectionName(), teamId, effectiveDimension)
                                        .then(Mono.just(Tuples.of(collection, effectiveDimension)));
                            });
                })
                .flatMap(tuple -> {
                    KnowledgeHubCollection collection = tuple.getT1();
                    Integer effectiveDimension = tuple.getT2();

                    collection.setMilvusCollectionName(request.getMilvusCollectionName());
                    collection.setEmbeddingModel(request.getEmbeddingModel());
                    collection.setEmbeddingModelName(request.getEmbeddingModelName());
                    collection.setEmbeddingDimension(effectiveDimension);
                    collection.setLateChunkingEnabled(request.isLateChunkingEnabled());
                    collection.setUpdatedBy(updatedBy);
                    collection.setUpdatedAt(LocalDateTime.now());

                    return collectionRepository.save(collection)
                            .doOnSuccess(col -> log.info("Assigned Milvus collection {} to KnowledgeHub collection {}", col.getMilvusCollectionName(), col.getId()));
                });
    }

    @Override
    public Mono<KnowledgeHubCollection> removeMilvusCollection(String id, String teamId, String updatedBy) {
        log.info("Removing Milvus assignment from KnowledgeHub collection {} for team {}", id, teamId);

        return collectionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found: " + id)))
                .flatMap(collection -> {
                    if (!collection.getTeamId().equals(teamId)) {
                        return Mono.error(new RuntimeException("Collection not found or access denied: " + id));
                    }

                    collection.setMilvusCollectionName(null);
                    collection.setEmbeddingModel(null);
                    collection.setEmbeddingModelName(null);
                    collection.setEmbeddingDimension(null);
                    collection.setLateChunkingEnabled(false);
                    collection.setUpdatedBy(updatedBy);
                    collection.setUpdatedAt(LocalDateTime.now());

                    return collectionRepository.save(collection)
                            .doOnSuccess(col -> log.info("Removed Milvus assignment from collection {}", col.getId()));
                });
    }

    @Override
    public Mono<Map<String, Object>> searchCollection(String collectionId, String teamId, String query, Integer topK, Map<String, Object> metadataFilters) {
        if (!StringUtils.hasText(query)) {
            return Mono.error(new IllegalArgumentException("Search query must not be empty"));
        }

        int effectiveTopK = Optional.ofNullable(topK)
                .filter(value -> value > 0)
                .map(value -> Math.min(value, MAX_SEARCH_RESULTS))
                .orElse(DEFAULT_SEARCH_RESULTS);

        return collectionRepository.findById(collectionId)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found: " + collectionId)))
                .flatMap(collection -> {
                    if (!Objects.equals(collection.getTeamId(), teamId)) {
                        return Mono.error(new RuntimeException("Collection not found or access denied: " + collectionId));
                    }

                    if (!StringUtils.hasText(collection.getMilvusCollectionName())) {
                        return Mono.error(new IllegalStateException("Collection is not linked to a Milvus collection. Assign embeddings before searching."));
                    }

                    if (!StringUtils.hasText(collection.getEmbeddingModel()) || !StringUtils.hasText(collection.getEmbeddingModelName())) {
                        return Mono.error(new IllegalStateException("Embedding configuration is incomplete for this collection."));
                    }

                    log.info("Running semantic search for collection {} (Milvus: {}) using model {} / {}",
                            collectionId,
                            collection.getMilvusCollectionName(),
                            collection.getEmbeddingModel(),
                            collection.getEmbeddingModelName());

                    return milvusStoreService.searchRecord(
                                    collection.getMilvusCollectionName(),
                                    DEFAULT_TEXT_FIELD,
                                    query,
                                    teamId,
                                    collection.getEmbeddingModel(),
                                    collection.getEmbeddingModelName(),
                                    effectiveTopK,
                                    metadataFilters)
                            .doOnError(error -> log.error("Semantic search failed for collection {}: {}", collectionId, error.getMessage(), error));
                });
    }

    private Mono<MilvusCollectionInfo> requireMilvusCollectionForTeam(String collectionName, String teamId) {
        return milvusStoreService.listCollections(teamId, null)
                .flatMapMany(Flux::fromIterable)
                .filter(info -> info.getName().equals(collectionName))
                .singleOrEmpty()
                .switchIfEmpty(Mono.error(new RuntimeException("Milvus collection '" + collectionName + "' not found for team " + teamId)));
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> validateMilvusCollectionSchema(String collectionName, String teamId, Integer expectedDimension) {
        return milvusStoreService.getCollectionDetails()
                .flatMap(details -> {
                    Object collectionsObj = details.get("collections");
                    if (!(collectionsObj instanceof List<?> collections)) {
                        return Mono.error(new RuntimeException("Unable to read Milvus collection details"));
                    }

                    Optional<Map<String, Object>> maybeCollection = collections.stream()
                            .filter(Map.class::isInstance)
                            .map(map -> (Map<String, Object>) map)
                            .filter(map -> collectionName.equals(map.get("name")))
                            .findFirst();

                    if (maybeCollection.isEmpty()) {
                        return Mono.error(new RuntimeException("Milvus collection '" + collectionName + "' not found in collection details"));
                    }

                    Map<String, Object> collectionInfo = maybeCollection.get();
                    Object teamIdProperty = collectionInfo.get("teamId");
                    if (teamIdProperty != null && !Objects.equals(teamIdProperty, teamId)) {
                        return Mono.error(new RuntimeException("Milvus collection '" + collectionName + "' does not belong to team " + teamId));
                    }

                    Object schemaObj = collectionInfo.get("schema");
                    if (!(schemaObj instanceof List<?> schemaList)) {
                        return Mono.error(new RuntimeException("Unable to read schema for Milvus collection '" + collectionName + "'"));
                    }

                    MilvusSchemaValidator.ValidationResult validationResult = MilvusSchemaValidator.validate(
                            schemaList.stream()
                                    .filter(Map.class::isInstance)
                                    .map(field -> (Map<String, Object>) field)
                                    .collect(Collectors.toList()),
                            expectedDimension
                    );

                    if (!validationResult.isValid()) {
                        String message = String.join("; ", validationResult.getIssues());
                        return Mono.error(new RuntimeException("Milvus collection '" + collectionName + "' schema validation failed: " + message));
                    }

                    return Mono.empty();
                });
    }
}

