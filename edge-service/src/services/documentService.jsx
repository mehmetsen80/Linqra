import axiosInstance from './axiosInstance';

export const documentService = {
  // Get all documents, optionally filtered by collection or status
  getAllDocuments: async (params = {}) => {
    try {
      const queryParams = new URLSearchParams();
      if (params.collectionId) queryParams.append('collectionId', params.collectionId);
      if (params.status) queryParams.append('status', params.status);
      
      const url = `/api/v1/documents${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
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
      const response = await axiosInstance.get(`/api/v1/documents/view/${documentId}`);
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
      const response = await axiosInstance.post('/api/v1/documents/upload/initiate', fileData);
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
      const response = await axiosInstance.post(`/api/v1/documents/upload/${documentId}/complete`, { s3Key });
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
      const response = await axiosInstance.get(`/api/v1/documents/${documentId}/status`);
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
      const response = await axiosInstance.get(`/api/v1/documents/view/${documentId}/download`);
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

  // Delete document
  deleteDocument: async (documentId) => {
    try {
      await axiosInstance.delete(`/api/v1/documents/${documentId}`);
      return {
        success: true
      };
    } catch (error) {
      console.error('Error deleting document:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to delete document'
      };
    }
  }
};

