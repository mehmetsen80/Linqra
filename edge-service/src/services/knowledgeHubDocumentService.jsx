import axiosInstance from './axiosInstance';

export const knowledgeHubDocumentService = {
  // Get all documents, optionally filtered by collection or status
  getAllDocuments: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.collectionId) queryParams.append('collectionId', params.collectionId);
      if (params.status) queryParams.append('status', params.status);
      
      const url = `/api/documents${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
      const response = await axiosInstance.get(url);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching documents:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch documents'
      };
    }
  },

  // Get document by ID
  getDocumentById: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching document:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch document'
      };
    }
  },

  // Initiate document upload
  initiateUpload: async (fileData) => {
    try {
      const response = await axiosInstance.post('/api/documents/upload/initiate', fileData);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error initiating upload:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to initiate upload'
      };
    }
  },

  // Complete document upload
  completeUpload: async (documentId, s3Key) => {
    try {
      const response = await axiosInstance.post(`/api/documents/upload/${documentId}/complete`, { s3Key });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error completing upload:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to complete upload'
      };
    }
  },

  // Get document status
  getDocumentStatus: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/${documentId}/status`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching document status:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch document status'
      };
    }
  },

  // Generate download URL
  generateDownloadUrl: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}/download`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error generating download URL:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to generate download URL'
      };
    }
  },

  // Generate download URL for processed JSON
  generateProcessedJsonDownloadUrl: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/view/${documentId}/processed/download`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error generating processed JSON download URL:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to generate processed JSON download URL'
      };
    }
  },

  // Delete document (soft delete - only if S3 file doesn't exist)
  deleteDocument: async (documentId) => {
    try {
      await axiosInstance.delete(`/api/documents/${documentId}`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error deleting document:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to delete document'
      };
    }
  },

  // Hard delete document (deletes everything including S3 files)
  hardDeleteDocument: async (documentId) => {
    try {
      await axiosInstance.delete(`/api/documents/${documentId}/hard`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error hard deleting document:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to hard delete document'
      };
    }
  },

  // Get document metadata
  getMetadata: async (documentId) => {
    try {
      const response = await axiosInstance.get(`/api/documents/metadata/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching metadata:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to fetch metadata',
        statusCode: error.response?.status // Include status code for retry logic
      };
    }
  },

  // Extract metadata from a processed document
  extractMetadata: async (documentId) => {
    try {
      const response = await axiosInstance.post(`/api/documents/metadata/extract/${documentId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error extracting metadata:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to extract metadata'
      };
    }
  }
};

