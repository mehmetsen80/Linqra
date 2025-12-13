package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AssignMilvusCollectionRequest;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.KnowledgeCategory;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.KnowledgeHubCollectionService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.validation.validator.MilvusSchemaValidator;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.LocalDateTime;
import java.util.HashMap;
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
    private final AuditLogHelper auditLogHelper;

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
                                        .flatMap(savedCollection -> {
                                            log.info("Created knowledge collection: {} with ID: {}", savedCollection.getName(), savedCollection.getId());
                                            
                                            // Build detailed audit context
                                            Map<String, Object> auditContext = new HashMap<>();
                                            auditContext.put("collectionName", savedCollection.getName());
                                            auditContext.put("description", savedCollection.getDescription());
                                            auditContext.put("categories", savedCollection.getCategories() != null ?
                                                savedCollection.getCategories().stream().map(Enum::name).collect(Collectors.toList()) :
                                                java.util.Collections.emptyList());
                                            auditContext.put("createdBy", savedCollection.getCreatedBy());
                                            auditContext.put("createdAt", savedCollection.getCreatedAt().toString());
                                            
                                            String auditReason = String.format("Knowledge Hub collection '%s' created in team %s", 
                                                savedCollection.getName(), teamId);
                                            
                                            // Log detailed audit event with collectionId populated
                                            return auditLogHelper.logDetailedEvent(
                                                AuditEventType.COLLECTION_CREATED,
                                                AuditActionType.CREATE,
                                                AuditResourceType.COLLECTION,
                                                savedCollection.getId(), // resourceId - the collection ID
                                                auditReason,
                                                auditContext,
                                                null, // documentId - not applicable for collection creation
                                                savedCollection.getId() // collectionId - the created collection
                                            ).thenReturn(savedCollection);
                                        });
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
                                
                                // Capture "before" state for audit
                                String oldName = collection.getName();
                                String oldDescription = collection.getDescription();
                                List<KnowledgeCategory> oldCategories = collection.getCategories();
                                
                                // Update collection
                                collection.setName(name);
                                collection.setDescription(description);
                                collection.setCategories(categories);
                                collection.setUpdatedBy(updatedBy);
                                collection.setUpdatedAt(LocalDateTime.now());
                                
                                return collectionRepository.save(collection)
                                        .flatMap(savedCollection -> {
                                            log.info("Updated knowledge collection: {} with ID: {}", savedCollection.getName(), savedCollection.getId());
                                            
                                            // Build detailed audit context
                                            Map<String, Object> auditContext = new HashMap<>();
                                            auditContext.put("collectionName", savedCollection.getName());
                                            auditContext.put("oldName", oldName);
                                            auditContext.put("newName", name);
                                            auditContext.put("oldDescription", oldDescription);
                                            auditContext.put("newDescription", description);
                                            auditContext.put("oldCategories", oldCategories != null ?
                                                oldCategories.stream().map(Enum::name).collect(Collectors.toList()) :
                                                java.util.Collections.emptyList());
                                            auditContext.put("newCategories", categories != null ?
                                                categories.stream().map(Enum::name).collect(Collectors.toList()) :
                                                java.util.Collections.emptyList());
                                            auditContext.put("updatedBy", updatedBy);
                                            auditContext.put("updatedAt", savedCollection.getUpdatedAt().toString());
                                            
                                            String auditReason = String.format("Knowledge Hub collection '%s' (ID: %s) updated", 
                                                savedCollection.getName(), savedCollection.getId());
                                            
                                            // Log detailed audit event
                                            return auditLogHelper.logDetailedEvent(
                                                AuditEventType.COLLECTION_UPDATED,
                                                AuditActionType.UPDATE,
                                                AuditResourceType.COLLECTION,
                                                savedCollection.getId(),
                                                auditReason,
                                                auditContext,
                                                null, // documentId
                                                savedCollection.getId() // collectionId
                                            ).thenReturn(savedCollection);
                                        });
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
                    
                    // Capture collection details before deletion for audit
                    String collectionName = collection.getName();
                    String collectionDescription = collection.getDescription();
                    List<KnowledgeCategory> collectionCategories = collection.getCategories();
                    String milvusCollectionName = collection.getMilvusCollectionName();
                    
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
                                        .flatMap(v -> {
                                            log.info("Deleted knowledge collection: {}", id);
                                            
                                            // Build detailed audit context
                                            Map<String, Object> auditContext = new HashMap<>();
                                            auditContext.put("collectionName", collectionName);
                                            auditContext.put("description", collectionDescription);
                                            auditContext.put("categories", collectionCategories != null ?
                                                collectionCategories.stream().map(Enum::name).collect(Collectors.toList()) :
                                                java.util.Collections.emptyList());
                                            auditContext.put("milvusCollectionName", milvusCollectionName);
                                            auditContext.put("documentCount", documentCount);
                                            auditContext.put("deletionTimestamp", LocalDateTime.now().toString());
                                            
                                            String auditReason = String.format("Knowledge Hub collection '%s' (ID: %s) deleted", 
                                                collectionName, id);
                                            
                                            // Log detailed audit event as part of reactive chain
                                            return auditLogHelper.logDetailedEvent(
                                                AuditEventType.COLLECTION_DELETED,
                                                AuditActionType.DELETE,
                                                AuditResourceType.COLLECTION,
                                                id,
                                                auditReason,
                                                auditContext,
                                                null, // documentId
                                                id // collectionId
                                            )
                                            .doOnError(auditError -> log.error("Failed to log audit event (collection deletion): {}", auditError.getMessage(), auditError))
                                            .onErrorResume(auditError -> Mono.empty()); // Don't fail if audit logging fails
                                        });
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

                    // Capture "before" state for audit
                    String oldMilvusCollectionName = collection.getMilvusCollectionName();
                    String oldEmbeddingModel = collection.getEmbeddingModel();
                    String oldEmbeddingModelName = collection.getEmbeddingModelName();
                    Integer oldEmbeddingDimension = collection.getEmbeddingDimension();
                    boolean oldLateChunkingEnabled = collection.isLateChunkingEnabled();
                    
                    collection.setMilvusCollectionName(request.getMilvusCollectionName());
                    collection.setEmbeddingModel(request.getEmbeddingModel());
                    collection.setEmbeddingModelName(request.getEmbeddingModelName());
                    collection.setEmbeddingDimension(effectiveDimension);
                    collection.setLateChunkingEnabled(request.isLateChunkingEnabled());
                    collection.setUpdatedBy(updatedBy);
                    collection.setUpdatedAt(LocalDateTime.now());

                    return collectionRepository.save(collection)
                            .flatMap(savedCollection -> {
                                log.info("Assigned Milvus collection {} to KnowledgeHub collection {}", savedCollection.getMilvusCollectionName(), savedCollection.getId());
                                
                                // Build detailed audit context
                                Map<String, Object> auditContext = new HashMap<>();
                                auditContext.put("collectionName", savedCollection.getName());
                                auditContext.put("oldMilvusCollectionName", oldMilvusCollectionName);
                                auditContext.put("newMilvusCollectionName", request.getMilvusCollectionName());
                                auditContext.put("oldEmbeddingModel", oldEmbeddingModel);
                                auditContext.put("newEmbeddingModel", request.getEmbeddingModel());
                                auditContext.put("oldEmbeddingModelName", oldEmbeddingModelName);
                                auditContext.put("newEmbeddingModelName", request.getEmbeddingModelName());
                                auditContext.put("oldEmbeddingDimension", oldEmbeddingDimension);
                                auditContext.put("newEmbeddingDimension", effectiveDimension);
                                auditContext.put("oldLateChunkingEnabled", oldLateChunkingEnabled);
                                auditContext.put("newLateChunkingEnabled", request.isLateChunkingEnabled());
                                auditContext.put("updatedBy", updatedBy);
                                auditContext.put("updatedAt", savedCollection.getUpdatedAt().toString());
                                
                                String auditReason = String.format("Milvus collection '%s' assigned to Knowledge Hub collection '%s'", 
                                    request.getMilvusCollectionName(), savedCollection.getName());
                                
                                // Log detailed audit event
                                return auditLogHelper.logDetailedEvent(
                                    AuditEventType.COLLECTION_UPDATED,
                                    AuditActionType.UPDATE,
                                    AuditResourceType.COLLECTION,
                                    savedCollection.getId(),
                                    auditReason,
                                    auditContext,
                                    null, // documentId
                                    savedCollection.getId() // collectionId
                                ).thenReturn(savedCollection);
                            });
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

                    // Capture "before" state for audit
                    String oldMilvusCollectionName = collection.getMilvusCollectionName();
                    String oldEmbeddingModel = collection.getEmbeddingModel();
                    String oldEmbeddingModelName = collection.getEmbeddingModelName();
                    Integer oldEmbeddingDimension = collection.getEmbeddingDimension();
                    boolean oldLateChunkingEnabled = collection.isLateChunkingEnabled();
                    
                    collection.setMilvusCollectionName(null);
                    collection.setEmbeddingModel(null);
                    collection.setEmbeddingModelName(null);
                    collection.setEmbeddingDimension(null);
                    collection.setLateChunkingEnabled(false);
                    collection.setUpdatedBy(updatedBy);
                    collection.setUpdatedAt(LocalDateTime.now());

                    return collectionRepository.save(collection)
                            .flatMap(savedCollection -> {
                                log.info("Removed Milvus assignment from collection {}", savedCollection.getId());
                                
                                // Build detailed audit context
                                Map<String, Object> auditContext = new HashMap<>();
                                auditContext.put("collectionName", savedCollection.getName());
                                auditContext.put("removedMilvusCollectionName", oldMilvusCollectionName);
                                auditContext.put("removedEmbeddingModel", oldEmbeddingModel);
                                auditContext.put("removedEmbeddingModelName", oldEmbeddingModelName);
                                auditContext.put("removedEmbeddingDimension", oldEmbeddingDimension);
                                auditContext.put("removedLateChunkingEnabled", oldLateChunkingEnabled);
                                auditContext.put("updatedBy", updatedBy);
                                auditContext.put("updatedAt", savedCollection.getUpdatedAt().toString());
                                
                                String auditReason = String.format("Milvus collection assignment removed from Knowledge Hub collection '%s'", 
                                    savedCollection.getName());
                                
                                // Log detailed audit event
                                return auditLogHelper.logDetailedEvent(
                                    AuditEventType.COLLECTION_UPDATED,
                                    AuditActionType.UPDATE,
                                    AuditResourceType.COLLECTION,
                                    savedCollection.getId(),
                                    auditReason,
                                    auditContext,
                                    null, // documentId
                                    savedCollection.getId() // collectionId
                                ).thenReturn(savedCollection);
                            });
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
                            .flatMap(searchResults -> {
                                // Build detailed audit context
                                Map<String, Object> auditContext = new HashMap<>();
                                auditContext.put("collectionName", collection.getName());
                                auditContext.put("milvusCollectionName", collection.getMilvusCollectionName());
                                auditContext.put("embeddingModel", collection.getEmbeddingModel());
                                auditContext.put("embeddingModelName", collection.getEmbeddingModelName());
                                auditContext.put("query", query);
                                auditContext.put("topK", effectiveTopK);
                                auditContext.put("metadataFilters", metadataFilters != null ? metadataFilters : java.util.Collections.emptyMap());
                                auditContext.put("resultCount", searchResults != null && searchResults instanceof Map ? 
                                    ((Map<?, ?>) searchResults).size() : 0);
                                auditContext.put("searchTimestamp", LocalDateTime.now().toString());
                                
                                String auditReason = String.format("Semantic search executed on collection '%s' with query: '%s' (topK: %d)", 
                                    collection.getName(), query, effectiveTopK);
                                
                                // Log detailed audit event
                                return auditLogHelper.logDetailedEvent(
                                    AuditEventType.CHUNK_ACCESSED, // Using CHUNK_ACCESSED for search operations
                                    AuditActionType.READ,
                                    AuditResourceType.COLLECTION,
                                    collectionId,
                                    auditReason,
                                    auditContext,
                                    null, // documentId
                                    collectionId // collectionId
                                ).thenReturn(searchResults);
                            })
                            .onErrorResume(error -> {
                                log.error("Semantic search failed for collection {}: {}", collectionId, error.getMessage(), error);
                                
                                // Log failed search attempt
                                Map<String, Object> errorContext = new HashMap<>();
                                errorContext.put("collectionName", collection.getName());
                                errorContext.put("query", query);
                                errorContext.put("error", error.getMessage());
                                errorContext.put("searchTimestamp", LocalDateTime.now().toString());
                                
                                // Chain audit logging before returning error
                                return auditLogHelper.logDetailedEvent(
                                    AuditEventType.CHUNK_ACCESSED,
                                    AuditActionType.READ,
                                    AuditResourceType.COLLECTION,
                                    collectionId,
                                    String.format("Semantic search failed on collection '%s'", collection.getName()),
                                    errorContext,
                                    null,
                                    collectionId
                                )
                                .doOnError(auditError -> log.error("Failed to log audit event (search failed): {}", auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                .then(Mono.error(error)); // Return the original error after logging
                            });
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

