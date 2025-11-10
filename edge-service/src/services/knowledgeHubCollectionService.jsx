import axiosInstance from './axiosInstance';

export const knowledgeHubCollectionService = {
  // Get all knowledge collections for current team
  getAllCollections: async () => {
    try {
      const response = await axiosInstance.get('/api/knowledge/collections');
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
      const response = await axiosInstance.get(`/api/knowledge/collections/${collectionId}`);
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
      const response = await axiosInstance.post('/api/knowledge/collections', collectionData);
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
      const response = await axiosInstance.put(`/api/knowledge/collections/${collectionId}`, collectionData);
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
      await axiosInstance.delete(`/api/knowledge/collections/${collectionId}`);
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
      const response = await axiosInstance.get('/api/knowledge/collections/count');
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
  },

  // Assign Milvus collection to knowledge collection
  assignMilvusCollection: async (collectionId, payload) => {
    try {
      const response = await axiosInstance.put(`/api/knowledge/collections/${collectionId}/milvus`, payload);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error assigning Milvus collection:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to assign Milvus collection'
      };
    }
  },

  // Remove Milvus collection assignment
  removeMilvusCollection: async (collectionId) => {
    try {
      const response = await axiosInstance.delete(`/api/knowledge/collections/${collectionId}/milvus`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error removing Milvus collection assignment:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to remove Milvus collection assignment'
      };
    }
  },

  // Semantic search within a knowledge collection (Milvus-backed)
  searchCollection: async (collectionId, payload) => {
    try {
      const response = await axiosInstance.post(`/api/knowledge/collections/${collectionId}/search`, payload);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error searching knowledge collection:', error);
      return {
        success: false,
        error: error.response?.data?.error || error.response?.data?.message || 'Failed to search collection'
      };
    }
  }
};

