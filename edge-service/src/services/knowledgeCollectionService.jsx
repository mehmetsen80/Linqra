import axiosInstance from './axiosInstance';

export const knowledgeCollectionService = {
  // Get all knowledge collections for current team
  getAllCollections: async () => {
    try {
      const response = await axiosInstance.get('/api/v1/knowledge/collections');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching knowledge collections:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch knowledge collections'
      };
    }
  },

  // Get a specific knowledge collection by ID
  getCollectionById: async (collectionId) => {
    try {
      const response = await axiosInstance.get(`/api/v1/knowledge/collections/${collectionId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching knowledge collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch knowledge collection'
      };
    }
  },

  // Create a new knowledge collection
  createCollection: async (collectionData) => {
    try {
      const response = await axiosInstance.post('/api/v1/knowledge/collections', collectionData);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error creating knowledge collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to create knowledge collection'
      };
    }
  },

  // Update an existing knowledge collection
  updateCollection: async (collectionId, collectionData) => {
    try {
      const response = await axiosInstance.put(`/api/v1/knowledge/collections/${collectionId}`, collectionData);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error updating knowledge collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to update knowledge collection'
      };
    }
  },

  // Delete a knowledge collection
  deleteCollection: async (collectionId) => {
    try {
      await axiosInstance.delete(`/api/v1/knowledge/collections/${collectionId}`);
      return { success: true };
    } catch (error) {
      console.error('Error deleting knowledge collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to delete knowledge collection'
      };
    }
  },

  // Get collection count for current team
  getCollectionCount: async () => {
    try {
      const response = await axiosInstance.get('/api/v1/knowledge/collections/count');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching collection count:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch collection count'
      };
    }
  }
};

