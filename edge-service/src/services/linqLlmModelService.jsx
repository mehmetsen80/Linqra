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

  // Get specific LinqLlmModel configuration for a team and target
  getLlmModelByTarget: async (teamId, target) => {
    try {
      const response = await axiosInstance.get(`/api/linq-llm-models/team/${teamId}/target/${target}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching LLM model by target:', error);
      throw error;
    }
  },

  // Get specific LinqLlmModel configuration for a team, target, and modelType
  getLlmModelByTargetAndType: async (teamId, target, modelType) => {
    try {
      const response = await axiosInstance.get(`/api/linq-llm-models/team/${teamId}/target/${target}/model/${modelType}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching LLM model by target and model type:', error);
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
  }
};

