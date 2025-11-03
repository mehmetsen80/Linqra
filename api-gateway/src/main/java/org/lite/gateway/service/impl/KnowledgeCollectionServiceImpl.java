package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeCollection;
import org.lite.gateway.enums.KnowledgeCategory;
import org.lite.gateway.repository.DocumentRepository;
import org.lite.gateway.repository.KnowledgeCollectionRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.KnowledgeCollectionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeCollectionServiceImpl implements KnowledgeCollectionService {
    
    private final KnowledgeCollectionRepository collectionRepository;
    private final TeamRepository teamRepository;
    private final DocumentRepository documentRepository;
    
    @Override
    public Mono<KnowledgeCollection> createCollection(String name, String description, List<KnowledgeCategory> categories, String teamId, String createdBy) {
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
                                KnowledgeCollection collection = KnowledgeCollection.builder()
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
    public Mono<KnowledgeCollection> updateCollection(String id, String name, String description, List<KnowledgeCategory> categories, String teamId, String updatedBy) {
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
    public Flux<KnowledgeCollection> getCollectionsByTeam(String teamId) {
        log.info("Getting knowledge collections for team: {}", teamId);
        
        return collectionRepository.findByTeamId(Sort.by(Sort.Direction.ASC, "name"), teamId)
                .doOnComplete(() -> log.info("Retrieved knowledge collections for team: {}", teamId));
    }
    
    @Override
    public Mono<KnowledgeCollection> getCollectionById(String id, String teamId) {
        log.info("Getting knowledge collection: {} for team: {}", id, teamId);
        
        return collectionRepository.findByIdAndTeamId(id, teamId)
                .switchIfEmpty(Mono.error(new RuntimeException("Collection not found or access denied: " + id)));
    }
    
    @Override
    public Mono<Long> getCollectionCountByTeam(String teamId) {
        return collectionRepository.countByTeamId(teamId);
    }
}

