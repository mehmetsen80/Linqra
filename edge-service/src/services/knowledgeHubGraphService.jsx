import axiosInstance from './axiosInstance';

export const knowledgeHubGraphService = {
  /**
   * Get graph statistics for the current team
   * @returns {Promise} Promise with graph statistics
   */
  getGraphStatistics: async () => {
    try {
      const response = await axiosInstance.get('/api/knowledge-graph/statistics');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching graph statistics:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch graph statistics'
      };
    }
  },

  /**
   * Find entities by type with optional filters
   * @param {string} entityType - Type of entity (Form, Organization, Person, Date, Location, Document)
   * @param {object} filters - Optional filters (e.g., { documentId: "..." })
   * @returns {Promise} Promise with list of entities
   */
  findEntities: async (entityType, filters = {}) => {
    try {
      // Use direct Cypher query to get encrypted entity names (similar to relationships)
      // This bypasses backend decryption and returns raw encrypted values for table display
      const params = { teamId: filters.teamId || '' };
      let whereClause = 'WHERE e.teamId = $teamId';
      
      // Add documentId filter if provided
      if (filters.documentId) {
        whereClause += ' AND e.documentId = $documentId';
        params.documentId = filters.documentId;
      }
      
      const cypherQuery = `MATCH (e:${entityType}) ${whereClause} RETURN e.id as id, labels(e)[0] as type, e.name as name, e.encryptionKeyVersion as encryptionKeyVersion, e.name_encryption_version as name_encryption_version, properties(e) as properties LIMIT 1000`;
      
      // Call executeCypherQuery directly using axiosInstance
      const response = await axiosInstance.post('/api/knowledge-graph/query', {
        query: cypherQuery,
        parameters: params
      });
      
      const data = response.data;
      
      // Transform the results into entity format with encrypted names (similar to relationships)
      const entities = (Array.isArray(data) ? data : []).map(record => {
        const entity = {
          id: record.id,
          type: entityType,
          name: record.name, // This is the encrypted value from Neo4j (like relationships)
          encryptionKeyVersion: record.encryptionKeyVersion,
          name_encryption_version: record.name_encryption_version
        };
        
        // Include all other properties from properties(e) (excluding system/metadata fields)
        if (record.properties) {
          Object.keys(record.properties).forEach(key => {
            if (!['id', 'name', 'teamId', 'documentId', 'extractedAt', 'createdAt', 'updatedAt', 
                  'encryptionKeyVersion', 'name_encryption_version'].includes(key) &&
              !key.endsWith('_encryption_version')) {
              entity[key] = record.properties[key];
            }
          });
        }
        
        return entity;
      });
      
      return {
        success: true,
        data: entities
      };
    } catch (error) {
      console.error('Error finding entities:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to find entities'
      };
    }
  },

  /**
   * Find related entities for a given entity
   * @param {string} entityType - Type of the source entity
   * @param {string} entityId - ID of the source entity
   * @param {object} options - Options { relationshipType?, maxDepth? }
   * @returns {Promise} Promise with list of related entities
   */
  findRelatedEntities: async (entityType, entityId, options = {}) => {
    try {
      const params = new URLSearchParams();
      if (options.relationshipType) {
        params.append('relationshipType', options.relationshipType);
      }
      if (options.maxDepth) {
        params.append('maxDepth', options.maxDepth.toString());
      }

      const url = `/api/knowledge-graph/entities/${entityType}/${entityId}/related${params.toString() ? `?${params.toString()}` : ''}`;
      const response = await axiosInstance.get(url);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error finding related entities:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to find related entities'
      };
    }
  },

  /**
   * Queue entity extraction job for a document
   * @param {string} documentId - Document ID
   * @param {boolean} force - Force re-extraction even if already extracted
   * @returns {Promise} Promise with job info
   */
  extractEntitiesFromDocument: async (documentId, force = false) => {
    try {
      const response = await axiosInstance.post(
        `/api/knowledge-graph/documents/${documentId}/extract-entities?force=${force}`,
        {}
      );
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error queueing entity extraction:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to queue entity extraction'
      };
    }
  },

  /**
   * Queue relationship extraction job for a document
   * @param {string} documentId - Document ID
   * @param {boolean} force - Force re-extraction even if already extracted
   * @returns {Promise} Promise with job info
   */
  extractRelationshipsFromDocument: async (documentId, force = false) => {
    try {
      const response = await axiosInstance.post(
        `/api/knowledge-graph/documents/${documentId}/extract-relationships?force=${force}`,
        {}
      );
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error queueing relationship extraction:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to queue relationship extraction'
      };
    }
  },

  /**
   * Queue full extraction job (entities + relationships) for a document
   * @param {string} documentId - Document ID
   * @param {boolean} force - Force re-extraction even if already extracted
   * @returns {Promise} Promise with job info
   */
  extractAllFromDocument: async (documentId, force = false) => {
    try {
      const response = await axiosInstance.post(
        `/api/knowledge-graph/documents/${documentId}/extract-all?force=${force}`,
        {}
      );
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error queueing full extraction:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to queue full extraction'
      };
    }
  },

  /**
   * Get extraction job status
   * @param {string} jobId - Job ID
   * @returns {Promise} Promise with job status
   */
  getJobStatus: async (jobId) => {
    try {
      const response = await axiosInstance.get(`/api/knowledge-graph/jobs/${jobId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error getting job status:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to get job status'
      };
    }
  },

  /**
   * Get all extraction jobs for a document
   * @param {string} documentId - Document ID
   * @returns {Promise} Promise with list of jobs
   */
  getJobsForDocument: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/knowledge-graph/documents/${documentId}/jobs`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error getting jobs for document:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to get jobs'
      };
    }
  },

  /**
   * Cancel an extraction job
   * @param {string} jobId - Job ID
   * @returns {Promise} Promise with cancellation result
   */
  cancelJob: async (jobId) => {
    try {
      const response = await axiosInstance.post(`/api/knowledge-graph/jobs/${jobId}/cancel`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error cancelling job:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to cancel job'
      };
    }
  },

  /**
   * Delete an entity from the graph
   * @param {string} entityType - Type of entity
   * @param {string} entityId - ID of entity
   * @returns {Promise} Promise with deletion result
   */
  deleteEntity: async (entityType, entityId) => {
    try {
      await axiosInstance.delete(`/api/knowledge-graph/entities/${entityType}/${entityId}`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error deleting entity:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to delete entity'
      };
    }
  },

  /**
   * Delete all entities of a specific type for the current team
   * @param {string} entityType - Type of entity (Form, Organization, Person, Date, Location, Document)
   * @returns {Promise} Promise with deletion result including deletedCount
   */
  deleteAllEntitiesByType: async (entityType) => {
    try {
      const response = await axiosInstance.delete(`/api/knowledge-graph/entities/${entityType}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error deleting all entities by type:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to delete all entities'
      };
    }
  },

  /**
   * Execute a custom Cypher query
   * @param {string} cypherQuery - Cypher query string
   * @param {object} parameters - Query parameters
   * @returns {Promise} Promise with query results
   */
  executeCypherQuery: async (cypherQuery, parameters = {}) => {
    try {
      const response = await axiosInstance.post('/api/knowledge-graph/query', {
        query: cypherQuery,
        parameters: parameters
      });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error executing Cypher query:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to execute query'
      };
    }
  },

  /**
   * Find relationships with optional filters
   * @param {object} filters - Optional filters (e.g., { relationshipType: "...", documentId: "..." })
   * @returns {Promise} Promise with list of relationships
   */
  findRelationships: async (filters = {}) => {
    try {
      // Build Cypher query to fetch relationships
      // Note: teamId is stored on nodes, not relationships, so we filter by from.teamId and to.teamId
      const params = { teamId: filters.teamId || '' };
      let whereClause = 'WHERE from.teamId = $teamId AND to.teamId = $teamId';
      
      // Build MATCH clause based on relationship type filter
      let matchClause = 'MATCH (from)-[r]->(to)';
      if (filters.relationshipType && filters.relationshipType !== 'All') {
        matchClause = `MATCH (from)-[r:${filters.relationshipType}]->(to)`;
      }
      
      // Add documentId filter if provided
      if (filters.documentId) {
        whereClause += ' AND r.documentId = $documentId';
        params.documentId = filters.documentId;
      }
      
      const cypherQuery = `${matchClause} ${whereClause} RETURN type(r) as relationshipType, from.id as fromId, labels(from)[0] as fromType, from.name as fromName, from.encryptionKeyVersion as fromEncryptionVersion, from.name_encryption_version as fromNameEncryptionVersion, to.id as toId, labels(to)[0] as toType, to.name as toName, to.encryptionKeyVersion as toEncryptionVersion, to.name_encryption_version as toNameEncryptionVersion, properties(r) as relProps LIMIT 1000`;
      
      // Call executeCypherQuery directly using axiosInstance
      const response = await axiosInstance.post('/api/knowledge-graph/query', {
        query: cypherQuery,
        parameters: params
      });
      
      const data = response.data;
      
      // Transform the results into a more readable format
      const relationships = (Array.isArray(data) ? data : []).map(record => {
        const relationship = {
          relationshipType: record.relationshipType,
          fromEntity: {
            id: record.fromId,
            type: record.fromType,
            name: record.fromName,
            encryptionKeyVersion: record.fromEncryptionVersion,
            name_encryption_version: record.fromNameEncryptionVersion
          },
          toEntity: {
            id: record.toId,
            type: record.toType,
            name: record.toName,
            encryptionKeyVersion: record.toEncryptionVersion,
            name_encryption_version: record.toNameEncryptionVersion
          },
          properties: {}
        };
        
        // Extract relationship properties (excluding system properties)
        // relProps is a Map returned from properties(r)
        if (record.relProps) {
          Object.keys(record.relProps).forEach(key => {
            if (!['documentId', 'extractedAt', 'createdAt', 'updatedAt'].includes(key)) {
              relationship.properties[key] = record.relProps[key];
            }
          });
        }
        
        return relationship;
      });
      
      return {
        success: true,
        data: relationships
      };
    } catch (error) {
      console.error('Error finding relationships:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.message || 'Failed to find relationships'
      };
    }
  },

  /**
   * Get current encryption key version
   * @returns {Promise} Promise with current encryption key version
   */
  getCurrentEncryptionKeyVersion: async () => {
    try {
      const response = await axiosInstance.get('/api/knowledge-graph/encryption/version');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error getting current encryption key version:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to get encryption key version'
      };
    }
  },

  /**
   * Decrypt properties (for ADMIN/SUPER_ADMIN only)
   * @param {object} properties - Properties map that may contain encrypted values
   * @returns {Promise} Promise with decrypted properties
   */
  decryptProperties: async (properties) => {
    try {
      const response = await axiosInstance.post('/api/knowledge-graph/properties/decrypt', {
        properties: properties
      });
      
      // Response.data is the decrypted properties map directly
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error decrypting properties:', error);
      // Check for 403 Forbidden (access denied)
      if (error.response?.status === 403) {
        return {
          success: false,
          error: 'Access denied: Only administrators can decrypt properties'
        };
      }
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to decrypt properties'
      };
    }
  }
};

