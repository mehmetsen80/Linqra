import axiosInstance from './axiosInstance';

export const linqLlmModelService = {
  // Get LinqLlmModel configuration for a team
  getTeamConfiguration: async (teamId) => {
    try {
      const response = await axiosInstance.get(`/api/linq-llm-models/team/${teamId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching team configuration:', error);
      throw error;
    }
  },

  // Get all LinqLlmModel configurations for a team and model category (returns array)
  getLlmModelByModelCategory: async (teamId, modelCategory) => {
    try {
      const response = await axiosInstance.get(`/api/linq-llm-models/team/${teamId}/modelCategory/${modelCategory}`);
      // The backend now returns ResponseEntity<?> which might be an array
      return Array.isArray(response.data) ? response.data : [response.data];
    } catch (error) {
      console.error('Error fetching LLM model by modelCategory:', error);
      throw error;
    }
  },

  // Get all LinqLlmModel configurations for a team and multiple model categories (returns array)
  getLlmModelByModelCategories: async (teamId, modelCategoryList) => {
    try {
      const response = await axiosInstance.post(`/api/linq-llm-models/team/${teamId}/modelCategories`, modelCategoryList);
      return Array.isArray(response.data) ? response.data : [];
    } catch (error) {
      console.error('Error fetching LLM models by model categories:', error);
      throw error;
    }
  },

  // Get specific LinqLlmModel configuration for a team, model category, and model name
  getLlmModelByModelCategoryAndModelName: async (teamId, modelCategory, modelName) => {
    try {
      const response = await axiosInstance.get(`/api/linq-llm-models/team/${teamId}/modelCategory/${modelCategory}/model/${modelName}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching LLM model by model category and model name:', error);
      throw error;
    }
  },

  // Save LinqLlmModel configuration
  saveConfiguration: async (configuration) => {
    try {
      const response = await axiosInstance.post('/api/linq-llm-models', configuration);
      return response.data;
    } catch (error) {
      console.error('Error saving configuration:', error);
      throw error;
    }
  },

  // Delete LinqLlmModel configuration
  deleteConfiguration: async (id) => {
    try {
      await axiosInstance.delete(`/api/linq-llm-models/${id}`);
      return { success: true };
    } catch (error) {
      console.error('Error deleting configuration:', error);
      throw error;
    }
  }
};

