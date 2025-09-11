package org.lite.gateway.enums;

public enum AgentCapability {
    MONGODB_ACCESS,         // Can access MongoDB databases
    MILVUS_ACCESS,          // Can access Milvus vector databases
    LLM_INTEGRATION,        // Can integrate with LLM services (OpenAI, Claude, etc.)
    HTTP_CLIENT,            // Can make HTTP requests to external services
    FILE_SYSTEM_ACCESS,     // Can read/write files from file system
    EMAIL_SENDING,          // Can send emails
    SMS_SENDING,            // Can send SMS messages
    SLACK_INTEGRATION,      // Can send Slack messages
    WEBHOOK_HANDLING,       // Can handle incoming webhooks
    CRON_SCHEDULING,        // Can handle cron-based scheduling
    EVENT_STREAMING,        // Can handle event streams
    DATA_ENCRYPTION,        // Can encrypt/decrypt data
    IMAGE_PROCESSING,       // Can process images
    PDF_PROCESSING,         // Can process PDF files
    JSON_PROCESSING,        // Can process JSON data
    XML_PROCESSING,         // Can process XML data
    CSV_PROCESSING,         // Can process CSV files
    TEMPLATE_RENDERING,     // Can render templates (HTML, text, etc.)
    METRICS_COLLECTION,     // Can collect and report metrics
    LOG_ANALYSIS,           // Can analyze log files
    BACKUP_OPERATIONS,      // Can perform backup operations
    CACHE_MANAGEMENT        // Can manage cache operations
} 