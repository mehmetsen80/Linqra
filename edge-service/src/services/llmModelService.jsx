import axiosInstance from './axiosInstance';

const EMBEDDING_MODEL_CATEGORIES = ['openai-embed', 'gemini-embed', 'cohere-embed'];

const llmModelService = {
  async getEmbeddingModels(teamId, categories = EMBEDDING_MODEL_CATEGORIES) {
    try {
      const response = await axiosInstance.post(
        `/api/linq-llm-models/team/${teamId}/modelCategories`,
        categories
      );
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching embedding models:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch embedding models'
      };
    }
  },

  async getAllModels(activeOnly = false) {
    try {
      const params = activeOnly ? '?active=true' : '';
      const response = await axiosInstance.get(`/api/llm-models${params}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async getModelById(id) {
    try {
      const response = await axiosInstance.get(`/api/llm-models/${id}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async getModelsByProvider(provider) {
    try {
      const response = await axiosInstance.get(`/api/llm-models/provider/${provider}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async createModel(model) {
    try {
      const response = await axiosInstance.post('/api/llm-models', model);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async updateModel(id, model) {
    try {
      const response = await axiosInstance.put(`/api/llm-models/${id}`, model);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async deleteModel(id) {
    try {
      const response = await axiosInstance.delete(`/api/llm-models/${id}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  async initializeDefaultModels() {
    try {
      const response = await axiosInstance.post('/api/llm-models/initialize-defaults');
      return response.data;
    } catch (error) {
      throw error;
    }
  }
};

export { llmModelService, EMBEDDING_MODEL_CATEGORIES };
export default llmModelService;

