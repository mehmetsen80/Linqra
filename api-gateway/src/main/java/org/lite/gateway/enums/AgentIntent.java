package org.lite.gateway.enums;

public enum AgentIntent {
    MONGODB_READ,           // Read data from MongoDB collections
    MONGODB_WRITE,          // Write/update data in MongoDB collections
    MILVUS_READ,            // Read vectors from Milvus vector database
    MILVUS_WRITE,           // Write vectors to Milvus vector database
    LLM_ANALYSIS,           // Perform LLM-based text analysis
    LLM_GENERATION,         // Generate text using LLMs
    API_INTEGRATION,        // Integrate with external APIs
    WORKFLOW_ORCHESTRATION, // Orchestrate complex workflows
    DATA_TRANSFORMATION,    // Transform data between formats
    NOTIFICATION_SENDING,   // Send notifications (email, SMS, Slack, etc.)
    FILE_PROCESSING,        // Process files (upload, download, parse)
    MONITORING,             // Monitor systems and metrics
    REPORTING,              // Generate reports and analytics
    SCHEDULING,             // Handle scheduled tasks
    EVENT_HANDLING          // Handle and process events
} 