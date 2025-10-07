package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentIntent;
import org.lite.gateway.enums.AgentCapability;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Document(collection = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    // Primary query: by team + enabled
    @CompoundIndex(name = "team_enabled_idx", def = "{'teamId': 1, 'enabled': 1}"),
    
    // Unique constraint: name within team
    @CompoundIndex(name = "team_name_unique_idx", def = "{'teamId': 1, 'name': 1}", unique = true),
    
    // Capability queries
    @CompoundIndex(name = "team_capabilities_idx", def = "{'teamId': 1, 'capabilities': 1}"),
    
    // Audit queries
    @CompoundIndex(name = "team_created_idx", def = "{'teamId': 1, 'createdAt': -1}")
})
public class Agent {
    @Id
    private String id;
    
    // Basic Information
    private String name;                    // "WhatsApp Analysis Agent"
    private String description;             // "Analyzes WhatsApp messages and categorizes them"
    
    // Team Association
    private String teamId;                  // Team that owns this agent (inherits team permissions)
    
    // Agent Behavior Configuration
    private Set<AgentIntent> supportedIntents;  // [MONGODB_READ, LLM_ANALYSIS, MILVUS_WRITE]
    
    // Agent Capabilities
    private Set<AgentCapability> capabilities;   // [MONGODB_ACCESS, LLM_INTEGRATION, MILVUS_ACCESS]
    
    // AI App Endpoints (provided by developers, accessed via /r/route-identifier/)
    @Field("app_endpoints")
    private Map<String, String> appEndpoints; // "analyze_messages": "/api/analyze", "save_results": "/api/save"
    
    // Agent Configuration
    @Builder.Default
    private boolean enabled = true;         // true/false - can be disabled without deletion
    
    // Audit Fields
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the agent
    private String updatedBy;               // User who last updated the agent
    

    
    public boolean canExecute() {
        return enabled; // Simplified - just check if agent is enabled
    }
    
} 