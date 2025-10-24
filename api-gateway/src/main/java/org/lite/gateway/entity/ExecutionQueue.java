package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "execution_queue")
public class ExecutionQueue {
    
    @Id
    private String id;
    
    private String executionId;
    private String agentId;
    private String agentName;
    private String taskId;
    private String taskName;
    private String teamId;
    private String userId;
    
    private String status; // QUEUED, STARTING, STARTED
    
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    
    private int priority; // Higher number = higher priority
    private String queuePosition; // Position in queue
    
    // Additional metadata
    private String description;
    private Map<String, Object> metadata;
}
