package org.lite.gateway.enums;

public enum AgentTaskType {
    DATA_PROCESSING,    // Process data from MongoDB, files, or APIs
    API_CALL,           // Make HTTP calls to external services or AI app endpoints
    WORKFLOW_TRIGGER,   // Trigger existing Linqra workflows by ID
    WORKFLOW_EMBEDDED,  // Execute embedded workflow steps directly
    LLM_ANALYSIS,       // Use LLMs for text analysis, categorization, etc.
    VECTOR_OPERATIONS,  // Operations with Milvus vector database
    NOTIFICATION,       // Send notifications (email, Slack, etc.)
    DATA_SYNC,          // Synchronize data between different systems
    MONITORING,         // Monitor system health, metrics, or business KPIs
    REPORTING,          // Generate and send reports
    CUSTOM_SCRIPT       // Execute custom scripts (JavaScript, Python, etc.)
} 