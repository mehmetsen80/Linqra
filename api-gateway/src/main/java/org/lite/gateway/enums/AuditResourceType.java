package org.lite.gateway.enums;

/**
 * Enumeration of audit resource types
 */
public enum AuditResourceType {
    // Authentication & Authorization
    AUTH,
    
    // User Management
    USER,
    
    // Team Management
    TEAM,
    
    // Document Management
    DOCUMENT,
    CHUNK,
    METADATA,
    
    // Knowledge Hub
    COLLECTION,
    GRAPH_ENTITY,
    GRAPH_RELATIONSHIP,
    GRAPH,
    
    // Export & Data Operations
    EXPORT,
    
    // Agent & Workflow
    AGENT,
    AGENT_TASK,
    AGENT_TASK_EXECUTION,
    WORKFLOW,
    WORKFLOW_STEP,
    LLM_MODEL,
    CHAT,
    
    // API Management
    API_KEY,
    API_ROUTE,
    API_ENDPOINT,
    
    // System & Configuration
    VAULT,
    SYSTEM,
    CONFIGURATION
}

