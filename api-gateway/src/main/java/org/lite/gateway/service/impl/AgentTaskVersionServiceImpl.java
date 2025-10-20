package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentTaskVersion;
import org.lite.gateway.dto.ErrorCode;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.exception.ResourceNotFoundException;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentTaskVersionRepository;
import org.lite.gateway.service.AgentTaskVersionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTaskVersionServiceImpl implements AgentTaskVersionService {
    
    private final AgentTaskVersionRepository agentTaskVersionRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentRepository agentRepository;
    
    @Override
    public Mono<AgentTaskVersion> createNewVersion(String taskId, AgentTask updatedTask, String changeDescription) {
        log.info("Creating new version for task: {} with description: {}", taskId, changeDescription);
        
        return agentTaskRepository.findById(taskId)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("AgentTask not found with id: " + taskId, ErrorCode.USER_NOT_FOUND)))
            .flatMap(existingTask -> {
                // Get the agent to extract teamId
                return agentRepository.findById(existingTask.getAgentId())
                    .switchIfEmpty(Mono.error(new ResourceNotFoundException("Agent not found with id: " + existingTask.getAgentId(), ErrorCode.USER_NOT_FOUND)))
                    .flatMap(agent -> {
                        // Simply update task and create ONE version record for the NEW state
                        return updateTaskAndCreateNewVersion(existingTask, updatedTask, changeDescription, agent);
                    });
            })
            .doOnSuccess(version -> log.info("Successfully created version {} for task: {}", version.getVersion(), taskId))
            .doOnError(error -> log.error("Error creating version for task {}: {}", taskId, error.getMessage()));
    }
    
    private Mono<AgentTaskVersion> updateTaskAndCreateNewVersion(AgentTask existingTask, AgentTask updatedTask, String changeDescription, Agent agent) {
        // Increment version number
        Integer newVersion = (existingTask.getVersion() != null ? existingTask.getVersion() : 1) + 1;
        
        // Update the existing task with new data and version (only non-null fields)
        if (updatedTask.getName() != null) existingTask.setName(updatedTask.getName());
        if (updatedTask.getDescription() != null) existingTask.setDescription(updatedTask.getDescription());
        if (updatedTask.getTaskType() != null) existingTask.setTaskType(updatedTask.getTaskType());
        if (updatedTask.getPriority() != 0) existingTask.setPriority(updatedTask.getPriority());
        // Always update enabled field (boolean primitive, can't be null)
        existingTask.setEnabled(updatedTask.isEnabled());
        if (updatedTask.getMaxRetries() != 0) existingTask.setMaxRetries(updatedTask.getMaxRetries());
        if (updatedTask.getTimeoutMinutes() != 0) existingTask.setTimeoutMinutes(updatedTask.getTimeoutMinutes());
        if (updatedTask.getCronExpression() != null) existingTask.setCronExpression(updatedTask.getCronExpression());
        if (updatedTask.getCronDescription() != null) existingTask.setCronDescription(updatedTask.getCronDescription());
        if (updatedTask.getExecutionTrigger() != null) existingTask.setExecutionTrigger(updatedTask.getExecutionTrigger());
        // Always update scheduleOnStartup (boolean primitive, can't be null)
        existingTask.setScheduleOnStartup(updatedTask.isScheduleOnStartup());
        
        // If linq_config is updated, auto-detect and set the appropriate task type
        if (updatedTask.getLinqConfig() != null) {
            existingTask.setLinqConfig(updatedTask.getLinqConfig());
            AgentTaskType detectedType = detectTaskTypeFromLinqConfig(updatedTask.getLinqConfig());
            if (detectedType != null) {
                existingTask.setTaskType(detectedType);
                log.info("Auto-detected task type as {} based on linq_config structure", detectedType);
            }
        }
        
        if (updatedTask.getApiConfig() != null) existingTask.setApiConfig(updatedTask.getApiConfig());
        if (updatedTask.getScriptContent() != null) existingTask.setScriptContent(updatedTask.getScriptContent());
        if (updatedTask.getScriptLanguage() != null) existingTask.setScriptLanguage(updatedTask.getScriptLanguage());
        existingTask.setVersion(newVersion);
        existingTask.setUpdatedAt(LocalDateTime.now());
        existingTask.setUpdatedBy(updatedTask.getUpdatedBy());
        
        // Save the updated task
        return agentTaskRepository.save(existingTask)
            .flatMap(savedTask -> {
                // Create version from the updated task
                AgentTaskVersion newVersionRecord = AgentTaskVersion.fromAgentTask(
                    savedTask, 
                    changeDescription, 
                    savedTask.getUpdatedBy()
                );
                newVersionRecord.setTeamId(agent.getTeamId());
                
                return agentTaskVersionRepository.save(newVersionRecord);
            });
    }
    
    /**
     * Detects the appropriate task type based on linq_config structure
     * @param linqConfig The linq configuration map
     * @return The detected AgentTaskType or null if cannot be determined
     */
    private AgentTaskType detectTaskTypeFromLinqConfig(Map<String, Object> linqConfig) {
        if (linqConfig == null || !linqConfig.containsKey("query")) {
            return null;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            
            // Check for WORKFLOW_TRIGGER: has workflowId
            if (query.containsKey("workflowId") && query.get("workflowId") != null) {
                String workflowId = query.get("workflowId").toString();
                if (!workflowId.trim().isEmpty()) {
                    return AgentTaskType.WORKFLOW_TRIGGER;
                }
            }
            
            // Check for WORKFLOW_EMBEDDED: has workflow array
            if (query.containsKey("workflow") && query.get("workflow") instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> workflow = (java.util.List<Object>) query.get("workflow");
                if (!workflow.isEmpty()) {
                    return AgentTaskType.WORKFLOW_EMBEDDED;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect task type from linq_config: {}", e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public Flux<AgentTaskVersion> getVersionHistory(String taskId) {
        log.info("Fetching version history for task: {}", taskId);
        return agentTaskVersionRepository.findByTaskIdOrderByVersionDesc(taskId)
            .doOnComplete(() -> log.info("Successfully fetched version history for task: {}", taskId))
            .doOnError(error -> log.error("Error fetching version history for task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<AgentTaskVersion> getVersion(String taskId, Integer version) {
        log.info("Fetching version {} for task: {}", version, taskId);
        return agentTaskVersionRepository.findByTaskIdAndVersion(taskId, version)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Version " + version + " not found for task: " + taskId, ErrorCode.USER_NOT_FOUND)))
            .doOnSuccess(v -> log.info("Successfully fetched version {} for task: {}", version, taskId))
            .doOnError(error -> log.error("Error fetching version {} for task {}: {}", version, taskId, error.getMessage()));
    }
    
    @Override
    public Mono<AgentTask> rollbackToVersion(String taskId, Integer version) {
        log.info("Rolling back task {} to version: {}", taskId, version);
        
        return agentTaskVersionRepository.findByTaskIdAndVersion(taskId, version)
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Version " + version + " not found for task: " + taskId, ErrorCode.USER_NOT_FOUND)))
            .flatMap(targetVersion -> {
                // Get current task
                return agentTaskRepository.findById(taskId)
                    .switchIfEmpty(Mono.error(new ResourceNotFoundException("AgentTask not found with id: " + taskId, ErrorCode.USER_NOT_FOUND)))
                    .flatMap(currentTask -> {
                        // Simply perform rollback without creating additional version records
                        return performRollback(currentTask, targetVersion);
                    });
            })
            .doOnSuccess(task -> log.info("Successfully rolled back task {} to version: {}", taskId, version))
            .doOnError(error -> log.error("Error rolling back task {} to version {}: {}", taskId, version, error.getMessage()));
    }
    
    private Mono<AgentTask> performRollback(AgentTask currentTask, AgentTaskVersion targetVersion) {
        // Restore task from version but keep the current version number incremented
        Integer newVersion = (currentTask.getVersion() != null ? currentTask.getVersion() : 1) + 1;
        
        AgentTask rolledBackTask = targetVersion.toAgentTask();
        rolledBackTask.setId(currentTask.getId()); // Keep the same ID
        rolledBackTask.setVersion(newVersion); // Increment version
        rolledBackTask.setUpdatedAt(LocalDateTime.now());
        rolledBackTask.setCreatedAt(currentTask.getCreatedAt()); // Keep original creation time
        rolledBackTask.setCreatedBy(currentTask.getCreatedBy()); // Keep original creator
        
        // Save the updated task and create a version record for the rollback
        return agentTaskRepository.save(rolledBackTask)
            .flatMap(savedTask -> {
                // Get agent for teamId
                return agentRepository.findById(savedTask.getAgentId())
                    .flatMap(agent -> {
                        // Create version record for the rollback
                        AgentTaskVersion rollbackVersionRecord = AgentTaskVersion.fromAgentTask(
                            savedTask,
                            "Rolled back to version " + targetVersion.getVersion(),
                            savedTask.getUpdatedBy()
                        );
                        rollbackVersionRecord.setTeamId(agent.getTeamId());
                        
                        return agentTaskVersionRepository.save(rollbackVersionRecord)
                            .thenReturn(savedTask);
                    });
            });
    }
    
    @Override
    public Mono<AgentTaskVersion> getLatestVersion(String taskId) {
        log.info("Fetching latest version for task: {}", taskId);
        return agentTaskVersionRepository.findLatestVersionsByTaskId(taskId)
            .next() // Take the first (latest) result
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("No versions found for task: " + taskId, ErrorCode.USER_NOT_FOUND)))
            .doOnSuccess(v -> log.info("Successfully fetched latest version {} for task: {}", v.getVersion(), taskId))
            .doOnError(error -> log.error("Error fetching latest version for task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<Boolean> versionExists(String taskId, Integer version) {
        return agentTaskVersionRepository.findByTaskIdAndVersion(taskId, version)
            .map(v -> true)
            .defaultIfEmpty(false);
    }
    
    @Override
    public Mono<Void> deleteAllVersionsForTask(String taskId) {
        log.info("Deleting all versions for task: {}", taskId);
        return agentTaskVersionRepository.deleteByTaskId(taskId)
            .doOnSuccess(v -> log.info("Successfully deleted all versions for task: {}", taskId))
            .doOnError(error -> log.error("Error deleting versions for task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<Long> getVersionCount(String taskId) {
        return agentTaskVersionRepository.countByTaskId(taskId);
    }
    
    @Override
    public Mono<String> cleanupDuplicateVersions(String taskId) {
        log.info("Cleaning up duplicate versions for task: {}", taskId);
        
        return agentTaskVersionRepository.findByTaskIdOrderByVersionDesc(taskId)
            .collectList()
            .flatMap(versions -> {
                // Group by version number
                java.util.Map<Integer, java.util.List<AgentTaskVersion>> versionGroups = versions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(AgentTaskVersion::getVersion));
                
                // Find duplicates and keep only the latest one for each version
                java.util.List<String> idsToDelete = new java.util.ArrayList<>();
                int duplicatesFound = 0;
                
                for (java.util.Map.Entry<Integer, java.util.List<AgentTaskVersion>> entry : versionGroups.entrySet()) {
                    java.util.List<AgentTaskVersion> duplicates = entry.getValue();
                    if (duplicates.size() > 1) {
                        duplicatesFound += duplicates.size() - 1;
                        log.info("Found {} duplicates for version {}: {}", 
                            duplicates.size(), entry.getKey(), 
                            duplicates.stream().map(v -> v.getId() + "(" + v.getCreatedAt() + ")").collect(java.util.stream.Collectors.joining(", ")));
                        
                        // Sort by createdAt descending (newest first) and keep the latest one
                        duplicates.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                        
                        // Mark all but the first (latest) for deletion
                        for (int i = 1; i < duplicates.size(); i++) {
                            log.info("Marking for deletion: version {} id {} created at {}", 
                                duplicates.get(i).getVersion(), duplicates.get(i).getId(), duplicates.get(i).getCreatedAt());
                            idsToDelete.add(duplicates.get(i).getId());
                        }
                        
                        log.info("Keeping: version {} id {} created at {}", 
                            duplicates.get(0).getVersion(), duplicates.get(0).getId(), duplicates.get(0).getCreatedAt());
                    }
                }
                
                if (idsToDelete.isEmpty()) {
                    return Mono.just("No duplicate versions found");
                }
                
                log.info("Found {} duplicate version records to delete for task: {}", duplicatesFound, taskId);
                
                // Delete the duplicates
                final int finalDuplicatesFound = duplicatesFound;
                return reactor.core.publisher.Flux.fromIterable(idsToDelete)
                    .flatMap(id -> agentTaskVersionRepository.deleteById(id))
                    .then(Mono.just(String.format("Successfully cleaned up %d duplicate version records", finalDuplicatesFound)));
            })
            .doOnSuccess(result -> log.info("Cleanup result for task {}: {}", taskId, result))
            .doOnError(error -> log.error("Error cleaning up duplicates for task {}: {}", taskId, error.getMessage()));
    }
} 