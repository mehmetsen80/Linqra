package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubGraphContextService;
import org.lite.gateway.service.Neo4jGraphService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of KnowledgeHubGraphContextService.
 * Enriches AI Assistant chat context with Knowledge Graph information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeHubGraphContextServiceImpl implements KnowledgeHubGraphContextService {

    private final Neo4jGraphService neo4jGraphService;
    private final KnowledgeHubDocumentRepository documentRepository;

    // Common entity types in the Knowledge Graph
    private static final List<String> ENTITY_TYPES = List.of(
            "Form", "Organization", "Person", "Date", "Location", "Document"
    );

    @Override
    public Mono<Map<String, Object>> enrichContextWithGraph(String userMessage, String teamId) {
        log.debug("Enriching context with Knowledge Graph for query: {} (team: {})", userMessage, teamId);
        
        return extractEntityMentions(userMessage, teamId)
                .flatMap(entityMentions -> {
                    if (entityMentions.isEmpty()) {
                        log.debug("No entity mentions found in query");
                        return Mono.just(createEmptyContext());
                    }
                    
                    log.debug("Found {} entity mentions in query", entityMentions.size());
                    return queryGraphForEntities(entityMentions, teamId);
                })
                .doOnSuccess(context -> {
                    if (context != null && !context.isEmpty()) {
                        log.info("Successfully enriched context with Knowledge Graph data");
                    }
                })
                .doOnError(error -> log.error("Error enriching context with Knowledge Graph: {}", error.getMessage()));
    }

    @Override
    public Mono<List<Map<String, Object>>> extractEntityMentions(String query, String teamId) {
        log.debug("Extracting entity mentions from query: {}", query);
        
        String lowerQuery = query.toLowerCase();
        
        // Check if query is asking for multiple forms (e.g., "forms", "all forms", "list of forms", "bring all forms")
        boolean isMultipleFormsQuery = (lowerQuery.contains("forms") || lowerQuery.contains("form")) &&
                (lowerQuery.contains("all") || lowerQuery.contains("list") || 
                 lowerQuery.contains("multiple") || lowerQuery.contains("category") ||
                 lowerQuery.contains("based") || lowerQuery.contains("bring") || 
                 lowerQuery.contains("related") || lowerQuery.contains("related to"));
        
        // If asking for multiple forms, extract all meaningful keywords from query
        if (isMultipleFormsQuery) {
            // Check if user is asking for ALL forms (no specific filter)
            boolean isAllFormsQuery = lowerQuery.matches(".*\\ball\\b.*forms?.*|.*forms?.*\\ball\\b.*|.*bring\\s+all.*forms?.*|.*all.*forms?.*related.*");
            
            if (isAllFormsQuery) {
                // User wants ALL forms - return everything
                log.debug("Detected 'all forms' query - returning all Form entities");
                return getAllForms(teamId);
            }
            
            List<String> keywords = extractKeywords(query);
            // Filter out common stop words and keep meaningful keywords
            List<String> meaningfulKeywords = keywords.stream()
                    .filter(keyword -> keyword.length() >= 3)
                    .filter(keyword -> !isCommonWord(keyword))
                    .collect(Collectors.toList());
            
            if (!meaningfulKeywords.isEmpty()) {
                // Use custom Cypher query to search Form entities by any keywords in their properties
                log.debug("Searching forms by keywords: {}", meaningfulKeywords);
                return searchFormsByKeywords(meaningfulKeywords, teamId);
            } else {
                // If no specific keywords but asking for forms, return all Form entities
                log.debug("No specific keywords found - returning all Form entities");
                return getAllForms(teamId);
            }
        }
        
        // Extract potential entity keywords (form numbers, organization acronyms, etc.)
        List<String> keywords = extractKeywords(query);
        
        if (keywords.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        // Search across all entity types for matching entities
        List<Mono<List<Map<String, Object>>>> searchMonos = new ArrayList<>();
        
        for (String entityType : ENTITY_TYPES) {
            // Try exact match on name or id
            Mono<List<Map<String, Object>>> typeSearch = neo4jGraphService.findEntities(entityType, Map.of(), teamId)
                    .collectList()
                    .map(entities -> {
                        List<Map<String, Object>> matches = new ArrayList<>();
                        for (Map<String, Object> entity : entities) {
                            String name = (String) entity.getOrDefault("name", "");
                            String id = (String) entity.getOrDefault("id", "");
                            String description = (String) entity.getOrDefault("description", "");
                            Object purpose = entity.getOrDefault("purpose", "");
                            
                            // Check if any keyword matches (case-insensitive, contains)
                            for (String keyword : keywords) {
                                String lowerKeyword = keyword.toLowerCase();
                                if ((name != null && name.toLowerCase().contains(lowerKeyword)) ||
                                    (id != null && id.toLowerCase().contains(lowerKeyword)) ||
                                    (description != null && description.toString().toLowerCase().contains(lowerKeyword)) ||
                                    (purpose != null && purpose.toString().toLowerCase().contains(lowerKeyword))) {
                                    Map<String, Object> mention = new HashMap<>();
                                    mention.put("type", entityType);
                                    mention.put("id", id);
                                    mention.put("name", name);
                                    mention.put("confidence", "medium"); // Could calculate based on match quality
                                    if (!matches.contains(mention)) {
                                        matches.add(mention);
                                    }
                                    break; // Found a match for this entity, no need to check other keywords
                                }
                            }
                        }
                        return matches;
                    });
            searchMonos.add(typeSearch);
        }
        
        // Combine all entity type searches
        return Flux.fromIterable(searchMonos)
                .flatMap(Mono::from)
                .collectList()
                .map(allMatches -> {
                    // Flatten and deduplicate
                    List<Map<String, Object>> allMentions = allMatches.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    // Remove duplicates based on type+id combination
                    Map<String, Map<String, Object>> uniqueMentions = new LinkedHashMap<>();
                    for (Map<String, Object> mention : allMentions) {
                        String key = mention.get("type") + ":" + mention.get("id");
                        uniqueMentions.putIfAbsent(key, mention);
                    }
                    
                    return (List<Map<String, Object>>) new ArrayList<>(uniqueMentions.values());
                })
                .doOnSuccess(mentions -> {
                    if (!mentions.isEmpty()) {
                        log.debug("Extracted {} entity mentions from query", mentions.size());
                    }
                });
    }
    
    /**
     * Search Form entities by any keywords extracted from the query.
     * Uses custom Cypher query to search in name, purpose, description, and other properties.
     * Works with ANY topic/category - not hardcoded.
     */
    private Mono<List<Map<String, Object>>> searchFormsByKeywords(List<String> keywords, String teamId) {
        log.debug("Searching Form entities by keywords: {}", keywords);
        
        // Build Cypher query to search Form entities by keywords in various properties
        StringBuilder cypher = new StringBuilder();
        cypher.append("MATCH (e:Form {teamId: $teamId}) ");
        cypher.append("WHERE ");
        
        Map<String, Object> params = new HashMap<>();
        params.put("teamId", teamId);
        
        boolean first = true;
        for (int i = 0; i < keywords.size(); i++) {
            if (!first) {
                cypher.append("OR ");
            }
            String keyword = keywords.get(i).toLowerCase();
            String paramName = "keyword" + i;
            params.put(paramName, keyword);
            
            // Search in name, id, description, purpose, and other string properties
            cypher.append("(toLower(toString(e.name)) CONTAINS $").append(paramName).append(" OR ");
            cypher.append("toLower(toString(e.id)) CONTAINS $").append(paramName).append(" OR ");
            cypher.append("toLower(toString(e.description)) CONTAINS $").append(paramName).append(" OR ");
            cypher.append("toLower(toString(e.purpose)) CONTAINS $").append(paramName).append(") ");
            
            first = false;
        }
        
        cypher.append("RETURN e.id as id, e.name as name, e.documentId as documentId, e.purpose as purpose, e.description as description LIMIT 100");
        
        return neo4jGraphService.executeQuery(cypher.toString(), params)
                .collectList()
                .map(records -> {
                    List<Map<String, Object>> mentions = new ArrayList<>();
                    for (Map<String, Object> record : records) {
                        Object idObj = record.get("id");
                        if (idObj != null) {
                            Map<String, Object> mention = new HashMap<>();
                            mention.put("type", "Form");
                            mention.put("id", idObj.toString());
                            mention.put("name", record.getOrDefault("name", idObj).toString());
                            mention.put("confidence", "high");
                            // Include documentId for later use
                            if (record.get("documentId") != null) {
                                mention.put("documentId", record.get("documentId").toString());
                            }
                            mentions.add(mention);
                        }
                    }
                    log.debug("Found {} Form entities matching keywords: {}", mentions.size(), keywords);
                    return mentions;
                })
                .doOnError(error -> log.warn("Error searching forms by keywords: {}", error.getMessage()))
                .onErrorReturn(Collections.emptyList());
    }
    
    /**
     * Get all Form entities when no specific keywords are provided.
     * Used when user asks for "all forms" or "list of forms" without specific category.
     */
    private Mono<List<Map<String, Object>>> getAllForms(String teamId) {
        log.debug("Fetching all Form entities for team: {}", teamId);
        
        return neo4jGraphService.findEntities("Form", Map.of(), teamId)
                .collectList()
                .map(entities -> {
                    List<Map<String, Object>> mentions = new ArrayList<>();
                    for (Map<String, Object> entity : entities) {
                        Map<String, Object> mention = new HashMap<>();
                        mention.put("type", "Form");
                        mention.put("id", entity.get("id"));
                        mention.put("name", entity.getOrDefault("name", entity.get("id")));
                        mention.put("confidence", "medium");
                        if (entity.get("documentId") != null) {
                            mention.put("documentId", entity.get("documentId").toString());
                        }
                        mentions.add(mention);
                    }
                    log.debug("Found {} Form entities", mentions.size());
                    return mentions;
                })
                .doOnError(error -> log.warn("Error fetching all forms: {}", error.getMessage()))
                .onErrorReturn(Collections.emptyList());
    }

    @Override
    public Mono<Map<String, Object>> queryGraphForEntities(List<Map<String, Object>> entityMentions, String teamId) {
        log.debug("Querying Knowledge Graph for {} entity mentions", entityMentions.size());
        
        if (entityMentions.isEmpty()) {
            return Mono.just(createEmptyContext());
        }
        
        // Group mentions by type
        Map<String, List<Map<String, Object>>> mentionsByType = entityMentions.stream()
                .collect(Collectors.groupingBy(m -> (String) m.get("type")));
        
        // Query for each entity type and collect entities
        List<Mono<List<Map<String, Object>>>> entityQueries = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : mentionsByType.entrySet()) {
            String entityType = entry.getKey();
            List<String> entityIds = entry.getValue().stream()
                    .map(m -> (String) m.get("id"))
                    .collect(Collectors.toList());
            
            // Find entities by id
            Mono<List<Map<String, Object>>> typeQuery = neo4jGraphService.findEntities(entityType, Map.of(), teamId)
                    .filter(entity -> entityIds.contains(entity.get("id")))
                    .collectList();
            
            entityQueries.add(typeQuery);
        }
        
        // Execute all entity queries in parallel
        return Flux.fromIterable(entityQueries)
                .flatMap(Mono::from)
                .collectList()
                .flatMap(allEntities -> {
                    // Flatten entities
                    List<Map<String, Object>> entities = allEntities.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    if (entities.isEmpty()) {
                        return Mono.just(createEmptyContext());
                    }
                    
                    log.debug("Found {} entities in Knowledge Graph", entities.size());
                    
                    // Get related entities for each entity (1-hop relationships)
                    List<Mono<Map<String, Object>>> relatedEntityQueries = entities.stream()
                            .map(entity -> {
                                String entityType = (String) entity.get("type");
                                String entityId = (String) entity.get("id");
                                
                                return neo4jGraphService.findRelatedEntities(entityType, entityId, null, 1, teamId)
                                        .collectList()
                                        .map(related -> {
                                            Map<String, Object> enriched = new HashMap<>(entity);
                                            enriched.put("relatedEntities", related);
                                            return enriched;
                                        })
                                        .onErrorResume(error -> {
                                            log.warn("Error fetching related entities for {}:{}: {}", 
                                                    entityType, entityId, error.getMessage());
                                            Map<String, Object> enriched = new HashMap<>(entity);
                                            enriched.put("relatedEntities", Collections.emptyList());
                                            return Mono.just(enriched);
                                        });
                            })
                            .collect(Collectors.toList());
                    
                    return Flux.fromIterable(relatedEntityQueries)
                            .flatMap(Mono::from)
                            .collectList()
                            .flatMap(enrichedEntities -> {
                                // Extract unique documentIds from entities
                                Set<String> documentIds = enrichedEntities.stream()
                                        .map(entity -> (String) entity.get("documentId"))
                                        .filter(id -> id != null && !id.isEmpty())
                                        .collect(Collectors.toSet());
                                
                                // Fetch documents for these documentIds
                                Mono<List<Map<String, Object>>> documentsMono;
                                if (!documentIds.isEmpty()) {
                                    log.debug("Found {} unique documentIds in entities, fetching documents", documentIds.size());
                                    documentsMono = Flux.fromIterable(documentIds)
                                            .flatMap(docId -> documentRepository.findByDocumentId(docId)
                                                    .map(doc -> {
                                                        Map<String, Object> docMap = new HashMap<>();
                                                        docMap.put("documentId", doc.getDocumentId());
                                                        docMap.put("fileName", doc.getFileName());
                                                        docMap.put("id", doc.getId());
                                                        return docMap;
                                                    })
                                                    .onErrorResume(error -> {
                                                        log.warn("Error fetching document {}: {}", docId, error.getMessage());
                                                        return Mono.empty();
                                                    }))
                                            .collectList();
                                } else {
                                    documentsMono = Mono.just(Collections.emptyList());
                                }
                                
                                return documentsMono.map(documents -> {
                                    // Build context map
                                    Map<String, Object> context = new HashMap<>();
                                    context.put("entities", enrichedEntities);
                                    context.put("entityCount", enrichedEntities.size());
                                    context.put("documents", documents);
                                    context.put("documentCount", documents.size());
                                    
                                    // Build summary with document information
                                    String summary = buildGraphSummary(enrichedEntities, documents);
                                    context.put("summary", summary);
                                    
                                    // Extract relationships
                                    List<Map<String, Object>> relationships = extractRelationships(enrichedEntities);
                                    context.put("relationships", relationships);
                                    
                                    return context;
                                });
                            });
                })
                .doOnSuccess(context -> {
                    if (context != null && !context.isEmpty()) {
                        log.debug("Successfully queried Knowledge Graph - found {} entities and {} documents", 
                                context.get("entityCount"), context.get("documentCount"));
                    }
                });
    }
    
    /**
     * Extract keywords from query for entity matching.
     * Simple approach: looks for form numbers (I-130, I-485), common acronyms, etc.
     */
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        
        // Extract form numbers (e.g., I-130, I-485, I-864)
        java.util.regex.Pattern formPattern = java.util.regex.Pattern.compile("\\b[I-Z]-\\d{3}\\b", 
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher formMatcher = formPattern.matcher(query);
        while (formMatcher.find()) {
            keywords.add(formMatcher.group().toUpperCase());
        }
        
        // Extract potential acronyms (2-5 uppercase letters)
        java.util.regex.Pattern acronymPattern = java.util.regex.Pattern.compile("\\b[A-Z]{2,5}\\b");
        java.util.regex.Matcher acronymMatcher = acronymPattern.matcher(query);
        while (acronymMatcher.find()) {
            String acronym = acronymMatcher.group();
            // Skip common words that are acronyms but not entities
            if (!acronym.equals("US") && !acronym.equals("THE") && !acronym.equals("AND") && !acronym.equals("FOR")) {
                keywords.add(acronym);
            }
        }
        
        // Add whole query words (filtered) for semantic matching
        String[] words = query.split("\\s+");
        for (String word : words) {
            word = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (word.length() >= 3 && !isCommonWord(word)) {
                keywords.add(word);
            }
        }
        
        return keywords.stream().distinct().collect(Collectors.toList());
    }
    
    private boolean isCommonWord(String word) {
        // Common stop words that are unlikely to be entity names
        Set<String> stopWords = Set.of("the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "should", "could",
                "can", "may", "might", "must", "shall", "what", "when", "where", "who", "which",
                "how", "why", "this", "that", "these", "those", "with", "from", "about", "into",
                "onto", "through", "during", "including", "against", "among", "throughout");
        return stopWords.contains(word);
    }
    
    private Map<String, Object> createEmptyContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("entities", Collections.emptyList());
        context.put("entityCount", 0);
        context.put("documents", Collections.emptyList());
        context.put("documentCount", 0);
        context.put("relationships", Collections.emptyList());
        context.put("summary", "No relevant entities found in the Knowledge Graph for this query.");
        return context;
    }
    
    private String buildGraphSummary(List<Map<String, Object>> entities, List<Map<String, Object>> documents) {
        if (entities.isEmpty()) {
            return "No entities found in the Knowledge Graph.";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("The Knowledge Graph contains ").append(entities.size()).append(" relevant entity/entities: ");
        
        List<String> entityDescriptions = new ArrayList<>();
        for (Map<String, Object> entity : entities) {
            String type = (String) entity.getOrDefault("type", "Unknown");
            String name = (String) entity.getOrDefault("name", (String) entity.getOrDefault("id", "Unnamed"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> related = (List<Map<String, Object>>) entity.getOrDefault("relatedEntities", Collections.emptyList());
            
            StringBuilder descBuilder = new StringBuilder();
            descBuilder.append(String.format("%s (%s)", name, type));
            if (!related.isEmpty()) {
                descBuilder.append(String.format(" with %d related entity/entities", related.size()));
            }
            
            // Add document information if available
            String documentId = (String) entity.get("documentId");
            if (documentId != null && !documents.isEmpty()) {
                documents.stream()
                        .filter(doc -> documentId.equals(doc.get("documentId")))
                        .findFirst()
                        .ifPresent(doc -> {
                            String fileName = (String) doc.getOrDefault("fileName", "Unknown");
                            descBuilder.append(String.format(" [Document: %s]", fileName));
                        });
            }
            
            entityDescriptions.add(descBuilder.toString());
        }
        
        summary.append(String.join(", ", entityDescriptions));
        summary.append(".");
        
        // Add document summary
        if (!documents.isEmpty()) {
            summary.append("\n\n");
            summary.append("Available documents (").append(documents.size()).append("): ");
            List<String> docNames = documents.stream()
                    .map(doc -> (String) doc.getOrDefault("fileName", "Unknown"))
                    .collect(Collectors.toList());
            summary.append(String.join(", ", docNames));
            summary.append(".");
        }
        
        return summary.toString();
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRelationships(List<Map<String, Object>> entities) {
        List<Map<String, Object>> relationships = new ArrayList<>();
        
        for (Map<String, Object> entity : entities) {
            List<Map<String, Object>> related = (List<Map<String, Object>>) entity.getOrDefault("relatedEntities", Collections.emptyList());
            for (Map<String, Object> relatedEntity : related) {
                Map<String, Object> rel = new HashMap<>();
                rel.put("fromEntity", Map.of(
                        "type", entity.get("type"),
                        "id", entity.get("id"),
                        "name", entity.getOrDefault("name", entity.get("id"))
                ));
                rel.put("toEntity", Map.of(
                        "type", relatedEntity.get("type"),
                        "id", relatedEntity.get("id"),
                        "name", relatedEntity.getOrDefault("name", relatedEntity.get("id"))
                ));
                rel.put("relationshipType", relatedEntity.getOrDefault("relationshipType", "RELATED_TO"));
                relationships.add(rel);
            }
        }
        
        return relationships;
    }
}

