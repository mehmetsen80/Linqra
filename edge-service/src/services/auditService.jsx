import axiosInstance from './axiosInstance';

/**
 * Service for audit log queries and archive management
 * Only ADMIN or SUPER_ADMIN can access audit logs
 */
export const auditService = {
    /**
     * Query audit logs with filters and pagination
     * @param {Object} request - Query parameters
     * @param {string} request.teamId - Team ID (optional, auto-set from context)
     * @param {string} request.startTime - Start time (ISO string)
     * @param {string} request.endTime - End time (ISO string)
     * @param {string} request.userId - Filter by user ID
     * @param {string[]} request.eventTypes - Filter by event types
     * @param {string} request.result - Filter by result (SUCCESS, FAILED, DENIED)
     * @param {string} request.documentId - Filter by document ID
     * @param {string} request.collectionId - Filter by collection ID
     * @param {string} request.resourceType - Filter by resource type
     * @param {number} request.page - Page number (0-indexed)
     * @param {number} request.size - Page size
     * @returns {Promise<Object>} Paginated audit logs response
     */
    queryAuditLogs: async (request) => {
        try {
            const response = await axiosInstance.post('/api/audit/logs/query', request);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error querying audit logs:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to query audit logs'
            };
        }
    },

    /**
     * Get audit trail for a specific document
     * @param {string} documentId - Document ID
     * @returns {Promise<Object>} Document audit trail
     */
    getDocumentAuditTrail: async (documentId) => {
        try {
            const response = await axiosInstance.get(`/api/audit/logs/document/${documentId}`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error getting document audit trail:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to get document audit trail'
            };
        }
    },

    /**
     * Get audit logs for a specific user
     * @param {string} userId - User ID
     * @param {string} startTime - Start time (ISO string, optional)
     * @param {string} endTime - End time (ISO string, optional)
     * @returns {Promise<Object>} User audit logs
     */
    getUserAuditLogs: async (userId, startTime = null, endTime = null) => {
        try {
            const params = {};
            if (startTime) params.startTime = startTime;
            if (endTime) params.endTime = endTime;

            const response = await axiosInstance.get(`/api/audit/logs/user/${userId}`, { params });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error getting user audit logs:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to get user audit logs'
            };
        }
    },

    /**
     * Get archival statistics
     * @returns {Promise<Object>} Archival stats (total, ready for archival, archived)
     */
    getArchivalStats: async () => {
        try {
            const response = await axiosInstance.get('/api/audit/stats');
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error getting archival stats:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to get archival stats'
            };
        }
    }
};

/**
 * Available audit event types for filtering
 */
export const AUDIT_EVENT_TYPES = {
    // Data Access Events
    DATA_ACCESS: [
        'DOCUMENT_ACCESSED',
        'DOCUMENT_VIEWED',
        'CHUNK_ACCESSED',
        'CHUNK_DECRYPTED',
        'METADATA_ACCESSED',
        'GRAPH_ENTITY_ACCESSED',
        'GRAPH_RELATIONSHIP_ACCESSED',
        'RAG_QUERY',
        'VECTOR_SEARCH'
    ],
    // Data Modification Events
    DATA_MODIFICATION: [
        'DOCUMENT_UPLOADED',
        'DOCUMENT_PROCESSING_STARTED',
        'DOCUMENT_PROCESSING_COMPLETED',
        'DOCUMENT_PROCESSING_FAILED',
        'DOCUMENT_DELETED',
        'DOCUMENT_HARD_DELETED',
        'CHUNK_CREATED',
        'CHUNK_DELETED',
        'METADATA_EXTRACTED',
        'METADATA_UPDATED',
        'METADATA_DELETED',
        'GRAPH_EXTRACTION_STARTED',
        'GRAPH_EXTRACTION_COMPLETED'
    ],
    // Export Events
    EXPORT: [
        'EXPORT_INITIATED',
        'EXPORT_COMPLETED',
        'EXPORT_FAILED',
        'EXPORT_DOWNLOADED'
    ],
    // Authentication Events
    AUTHENTICATION: [
        'USER_LOGIN',
        'USER_LOGOUT',
        'LOGIN_FAILED',
        'TOKEN_REFRESHED',
        'ROLE_CHANGED',
        'PERMISSION_MODIFIED',
        'TEAM_SWITCHED'
    ],
    // Security Events
    SECURITY: [
        'ACCESS_DENIED',
        'UNAUTHORIZED_ACCESS_ATTEMPT',
        'DECRYPTION_FAILED',
        'ENCRYPTION_FAILED',
        'VAULT_ACCESSED',
        'KEY_ROTATION_STARTED',
        'KEY_ROTATION_COMPLETED',
        'PII_DETECTED'
    ],
    // Administrative Events
    ADMINISTRATIVE: [
        'USER_CREATED',
        'USER_DELETED',
        'USER_UPDATED',
        'TEAM_CREATED',
        'TEAM_UPDATED',
        'TEAM_DELETED',
        'COLLECTION_CREATED',
        'COLLECTION_DELETED',
        'COLLECTION_UPDATED',
        'API_ROUTE_CREATED',
        'API_ROUTE_UPDATED',
        'API_ROUTE_DELETED',
        'CONFIGURATION_CHANGED'
    ],
    // Agent & Workflow Events
    AGENT_WORKFLOW: [
        'AGENT_TASK_EXECUTION_STARTED',
        'AGENT_TASK_EXECUTION_COMPLETED',
        'AGENT_TASK_EXECUTION_FAILED',
        'WORKFLOW_EXECUTION_STARTED',
        'WORKFLOW_EXECUTION_COMPLETED',
        'WORKFLOW_EXECUTION_FAILED',
        'WORKFLOW_STEP_EXECUTED'
    ],
    // LLM/Chat Events
    LLM_CHAT: [
        'LLM_REQUEST_STARTED',
        'LLM_REQUEST_COMPLETED',
        'LLM_REQUEST_FAILED',
        'CHAT_EXECUTION_STARTED',
        'CHAT_EXECUTION_COMPLETED',
        'CHAT_EXECUTION_FAILED'
    ],
    // System Events
    SYSTEM: [
        'ARCHIVAL_STARTED',
        'ARCHIVAL_COMPLETED',
        'ARCHIVAL_FAILED',
        'BACKUP_CREATED',
        'BACKUP_RESTORED'
    ]
};

/**
 * Get all event types as a flat array
 */
export const getAllEventTypes = () => {
    return Object.values(AUDIT_EVENT_TYPES).flat();
};

/**
 * Get event type category
 */
export const getEventTypeCategory = (eventType) => {
    for (const [category, types] of Object.entries(AUDIT_EVENT_TYPES)) {
        if (types.includes(eventType)) {
            return category;
        }
    }
    return 'OTHER';
};

/**
 * Format event type for display
 */
export const formatEventType = (eventType) => {
    if (!eventType) return '';
    return eventType
        .replace(/_/g, ' ')
        .toLowerCase()
        .replace(/\b\w/g, l => l.toUpperCase());
};

export default auditService;
