package org.lite.gateway.service;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentTaskVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentTaskVersionService {
    
    /**
     * Create a new version from the current task state
     */
    Mono<AgentTaskVersion> createNewVersion(String taskId, AgentTask updatedTask, String changeDescription);
    
    /**
     * Get all versions for a specific task
     */
    Flux<AgentTaskVersion> getVersionHistory(String taskId);
    
    /**
     * Get a specific version
     */
    Mono<AgentTaskVersion> getVersion(String taskId, Integer version);
    
    /**
     * Rollback a task to a specific version
     */
    Mono<AgentTask> rollbackToVersion(String taskId, Integer version);
    
    /**
     * Get the latest version for a task
     */
    Mono<AgentTaskVersion> getLatestVersion(String taskId);
    
    /**
     * Check if a version exists
     */
    Mono<Boolean> versionExists(String taskId, Integer version);
    
    /**
     * Delete all versions for a task (when task is deleted)
     */
    Mono<Void> deleteAllVersionsForTask(String taskId);
    
    /**
     * Get version count for a task
     */
    Mono<Long> getVersionCount(String taskId);
    
    /**
     * Clean up duplicate version records for a task
     */
    Mono<String> cleanupDuplicateVersions(String taskId);
} 