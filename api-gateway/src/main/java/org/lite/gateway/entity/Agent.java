package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentIntent;
import org.lite.gateway.enums.AgentCapability;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document(collection = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {
    @Id
    private String id;
    
    // Basic Information
    private String name;                    // "WhatsApp Analysis Agent"
    private String description;             // "Analyzes WhatsApp messages and categorizes them"
    
    // Team & AI App Association
    private String teamId;                  // Team that owns this agent (inherits team permissions)
    private String routeIdentifier;         // "komunas-app" - which AI app this agent serves
    
    // LLM Configuration (using existing LinqTool setup)
    private String primaryLinqToolId;       // Reference to existing LinqTool for LLM integration
    private List<AgentIntent> supportedIntents;  // [MONGODB_READ, LLM_ANALYSIS, MILVUS_WRITE]
    
    // Agent Capabilities
    @Field("capabilities")
    private Set<AgentCapability> capabilities;   // [MONGODB_ACCESS, LLM_INTEGRATION, MILVUS_ACCESS]
    
    // Scheduling (Quartz - Database backed)
    private String cronExpression;          // "0 */1 * * * *" (every hour)
    private String cronDescription;         // "Every hour at minute 0" (human-readable description)
    private boolean autoSchedule;           // true/false
    private String quartzJobKey;            // Quartz job identifier for persistence
    
    // AI App Endpoints (provided by developers, accessed via /r/route-identifier/)
    @Field("app_endpoints")
    private Map<String, String> appEndpoints; // "analyze_messages": "/api/analyze", "save_results": "/api/save"
    
    // Agent Configuration
    private String agentType;               // "data_analysis", "workflow_orchestrator", "monitoring"
    private boolean enabled;                // true/false - can be disabled without deletion
    
    // timeoutMinutes removed - each task controls its own timeout
    
    // Audit Fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the agent
    private String updatedBy;               // User who last updated the agent
    
    // Pre-persist and pre-update methods
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        enabled = true;
    }
    
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Timeout utility methods removed - use task-level timeout configuration
    
    public boolean isScheduled() {
        return autoSchedule && cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    public boolean canExecute() {
        return enabled; // Simplified - just check if agent is enabled
    }
    
} 