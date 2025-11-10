import axiosInstance from './axiosInstance';

export const milvusService = {
  getCollectionsForTeam: async (teamId, { collectionType } = {}) => {
    try {
      const response = await axiosInstance.get(`/api/milvus/collections/${teamId}`, {
        params: collectionType ? { type: collectionType } : undefined
      });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching Milvus collections:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch Milvus collections'
      };
    }
  },

  updateCollectionMetadata: async ({ teamId, collectionName, metadata }) => {
    try {
      const response = await axiosInstance.put(`/api/milvus/collections/${collectionName}/metadata`, {
        teamId,
        metadata
      });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error updating Milvus collection metadata:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to update Milvus collection metadata'
      };
    }
  },

  createCollection: async (payload) => {
    try {
      const response = await axiosInstance.post('/api/milvus/collections', payload);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error creating Milvus collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to create Milvus collection'
      };
    }
  },

  deleteCollection: async (collectionName, teamId) => {
    try {
      const response = await axiosInstance.delete(`/api/milvus/collections/${collectionName}`, {
        params: { teamId }
      });
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error deleting Milvus collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to delete Milvus collection'
      };
    }
  },

  verifyCollection: async (teamId, collectionName) => {
    try {
      const response = await axiosInstance.get(`/api/milvus/collections/${teamId}/${collectionName}/verify`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error verifying Milvus collection:', error);
      return {
        success: false,
        error: error.response?.data?.message || error.response?.data || 'Failed to verify Milvus collection'
      };
    }
  }
};

export default milvusService;

