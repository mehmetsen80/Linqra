package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AIAssistant;
import org.lite.gateway.repository.AIAssistantRepository;
import org.lite.gateway.service.AIAssistantService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAssistantServiceImpl implements AIAssistantService {
    
    private final AIAssistantRepository aiAssistantRepository;
    
    @Override
    public Mono<AIAssistant> createAssistant(AIAssistant assistant, String teamId, String createdBy) {
        log.info("Creating AI Assistant '{}' for team {}", assistant.getName(), teamId);
        
        assistant.setTeamId(teamId);
        assistant.setCreatedBy(createdBy);
        assistant.setUpdatedBy(createdBy);
        
        // Set default status if not provided
        if (assistant.getStatus() == null || assistant.getStatus().isEmpty()) {
            assistant.setStatus("DRAFT");
        }
        
        // Initialize default access control if not provided
        if (assistant.getAccessControl() == null) {
            AIAssistant.AccessControl accessControl = AIAssistant.AccessControl.builder()
                    .type("PRIVATE")
                    .allowAnonymousAccess(false)
                    .build();
            assistant.setAccessControl(accessControl);
        } else {
            // If created as PUBLIC, generate a public API key if missing
            if ("PUBLIC".equalsIgnoreCase(assistant.getAccessControl().getType())
                    && assistant.getAccessControl().getPublicApiKey() == null) {
                String apiKey = generateUniqueApiKey();
                assistant.getAccessControl().setPublicApiKey(apiKey);
            }
        }
        
        return aiAssistantRepository.save(assistant)
                .doOnSuccess(savedAssistant -> log.info("AI Assistant '{}' created successfully with ID: {}", 
                        savedAssistant.getName(), savedAssistant.getId()))
                .doOnError(error -> log.error("Failed to create AI Assistant '{}': {}", 
                        assistant.getName(), error.getMessage()));
    }
    
    @Override
    public Mono<AIAssistant> updateAssistant(String assistantId, AIAssistant assistantUpdates, String updatedBy) {
        log.info("Updating AI Assistant {}", assistantId);
        
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")))
                .flatMap(existingAssistant -> {
                    // Update basic fields
                    if (assistantUpdates.getName() != null) {
                        existingAssistant.setName(assistantUpdates.getName());
                    }
                    if (assistantUpdates.getDescription() != null) {
                        existingAssistant.setDescription(assistantUpdates.getDescription());
                    }
                    if (assistantUpdates.getStatus() != null) {
                        existingAssistant.setStatus(assistantUpdates.getStatus());
                    }
                    if (assistantUpdates.getDefaultModel() != null) {
                        existingAssistant.setDefaultModel(assistantUpdates.getDefaultModel());
                    }
                    if (assistantUpdates.getSystemPrompt() != null) {
                        existingAssistant.setSystemPrompt(assistantUpdates.getSystemPrompt());
                    }
                    if (assistantUpdates.getSelectedTasks() != null) {
                        existingAssistant.setSelectedTasks(assistantUpdates.getSelectedTasks());
                    }
                    if (assistantUpdates.getContextManagement() != null) {
                        existingAssistant.setContextManagement(assistantUpdates.getContextManagement());
                    }
                    if (assistantUpdates.getGuardrails() != null) {
                        existingAssistant.setGuardrails(assistantUpdates.getGuardrails());
                    }
                    // Allow updating access control (e.g., PRIVATE -> PUBLIC) from the main update endpoint
                    if (assistantUpdates.getAccessControl() != null) {
                        AIAssistant.AccessControl updates = assistantUpdates.getAccessControl();
                        AIAssistant.AccessControl current = existingAssistant.getAccessControl();
                        if (current == null) {
                            current = AIAssistant.AccessControl.builder().build();
                        }
                        
                        // Update basic access control fields
                        if (updates.getType() != null) {
                            current.setType(updates.getType());
                        }
                        if (updates.getAllowedDomains() != null) {
                            current.setAllowedDomains(updates.getAllowedDomains());
                        }
                        if (updates.getAllowAnonymousAccess() != null) {
                            current.setAllowAnonymousAccess(updates.getAllowAnonymousAccess());
                        }
                        
                        // If switching to PUBLIC and no API key exists yet, generate one
                        if ("PUBLIC".equalsIgnoreCase(current.getType())
                                && current.getPublicApiKey() == null) {
                            String apiKey = generateUniqueApiKey();
                            current.setPublicApiKey(apiKey);
                        }
                        
                        existingAssistant.setAccessControl(current);
                    }
                    
                    existingAssistant.setUpdatedBy(updatedBy);
                    
                    return aiAssistantRepository.save(existingAssistant);
                })
                .doOnSuccess(updatedAssistant -> log.info("AI Assistant {} updated successfully", assistantId))
                .doOnError(error -> log.error("Failed to update AI Assistant {}: {}", assistantId, error.getMessage()));
    }
    
    @Override
    public Mono<Boolean> deleteAssistant(String assistantId, String teamId) {
        log.info("Deleting AI Assistant {} for team {}", assistantId, teamId);
        
        return aiAssistantRepository.findById(assistantId)
                .filter(assistant -> teamId.equals(assistant.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found or access denied")))
                .flatMap(assistant -> {
                    // Hard delete - actually remove from database
                    return aiAssistantRepository.deleteById(assistantId).thenReturn(true);
                })
                .doOnSuccess(deleted -> log.info("AI Assistant {} deleted successfully", assistantId))
                .doOnError(error -> log.error("Failed to delete AI Assistant {}: {}", assistantId, error.getMessage()));
    }
    
    @Override
    public Mono<AIAssistant> getAssistantById(String assistantId) {
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")));
    }
    
    @Override
    public Flux<AIAssistant> getAssistantsByTeam(String teamId) {
        return aiAssistantRepository.findByTeamId(teamId);
    }
    
    @Override
    public Mono<AIAssistant> updateAccessControl(String assistantId, AIAssistant.AccessControl accessControl, String updatedBy) {
        log.info("Updating access control for AI Assistant {}", assistantId);
        
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")))
                .flatMap(existingAssistant -> {
                    existingAssistant.setAccessControl(accessControl);
                    existingAssistant.setUpdatedBy(updatedBy);
                    
                    // If making assistant public, generate API key if not exists
                    if ("PUBLIC".equals(accessControl.getType()) && accessControl.getPublicApiKey() == null) {
                        String apiKey = generateUniqueApiKey();
                        accessControl.setPublicApiKey(apiKey);
                    }
                    
                    return aiAssistantRepository.save(existingAssistant);
                })
                .doOnSuccess(updatedAssistant -> log.info("Access control updated for AI Assistant {}", assistantId))
                .doOnError(error -> log.error("Failed to update access control for AI Assistant {}: {}", 
                        assistantId, error.getMessage()));
    }
    
    @Override
    public Mono<Map<String, String>> generatePublicApiKey(String assistantId, String updatedBy) {
        log.info("Generating public API key for AI Assistant {}", assistantId);
        
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")))
                .flatMap(assistant -> {
                    if (assistant.getAccessControl() == null) {
                        assistant.setAccessControl(AIAssistant.AccessControl.builder()
                                .type("PRIVATE")
                                .build());
                    }
                    
                    String apiKey = generateUniqueApiKey();
                    assistant.getAccessControl().setPublicApiKey(apiKey);
                    assistant.getAccessControl().setType("PUBLIC");
                    assistant.setUpdatedBy(updatedBy);
                    
                    return aiAssistantRepository.save(assistant)
                            .map(saved -> Map.of("apiKey", apiKey, "assistantId", assistantId));
                })
                .doOnSuccess(result -> log.info("Public API key generated for AI Assistant {}", assistantId))
                .doOnError(error -> log.error("Failed to generate public API key for AI Assistant {}: {}", 
                        assistantId, error.getMessage()));
    }
    
    @Override
    public Mono<String> getWidgetScriptUrl(String assistantId) {
        log.info("Getting widget script URL for AI Assistant {}", assistantId);
        
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")))
                .map(assistant -> {
                    // Generate widget script URL
                    // Format: /widget/{publicApiKey} or /api/ai-assistants/{assistantId}/widget
                    if (assistant.getAccessControl() != null && 
                        "PUBLIC".equals(assistant.getAccessControl().getType()) &&
                        assistant.getAccessControl().getPublicApiKey() != null) {
                        return "/widget/" + assistant.getAccessControl().getPublicApiKey();
                    }
                    return "/api/ai-assistants/" + assistantId + "/widget";
                });
    }
    
    @Override
    public Mono<AIAssistant> updateWidgetConfig(String assistantId, AIAssistant.WidgetConfig widgetConfig, String updatedBy) {
        log.info("Updating widget config for AI Assistant {}", assistantId);
        
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found")))
                .flatMap(existingAssistant -> {
                    existingAssistant.setWidgetConfig(widgetConfig);
                    existingAssistant.setUpdatedBy(updatedBy);
                    
                    // Generate widget script URL if enabled
                    if (widgetConfig != null && Boolean.TRUE.equals(widgetConfig.getEnabled())) {
                        String scriptUrl = "/api/ai-assistants/" + assistantId + "/widget-script";
                        widgetConfig.setEmbedScriptUrl(scriptUrl);
                    }
                    
                    return aiAssistantRepository.save(existingAssistant);
                })
                .doOnSuccess(updatedAssistant -> log.info("Widget config updated for AI Assistant {}", assistantId))
                .doOnError(error -> log.error("Failed to update widget config for AI Assistant {}: {}", 
                        assistantId, error.getMessage()));
    }
    
    @Override
    public Mono<AIAssistant> getAssistantByPublicApiKey(String publicApiKey) {
        return aiAssistantRepository.findByPublicApiKey(publicApiKey)
                .switchIfEmpty(Mono.error(new RuntimeException("AI Assistant not found for API key")));
    }
    
    /**
     * Generate a unique API key
     */
    private String generateUniqueApiKey() {
        // Generate a unique API key format: linqra_pub_{uuid}
        return "linqra_pub_" + UUID.randomUUID().toString().replace("-", "");
    }
}

