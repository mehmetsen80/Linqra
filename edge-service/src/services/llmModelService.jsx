import axiosInstance from './axiosInstance';

const llmModelService = {
  /**
   * Get all LLM models
   * @param {boolean} activeOnly - If true, only return active models
   * @returns {Promise} Promise with list of LLM models
   */
  async getAllModels(activeOnly = false) {
    try {
      const params = activeOnly ? '?active=true' : '';
      const response = await axiosInstance.get(`/api/llm-models${params}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Get model by ID
   * @param {string} id - Model ID
   * @returns {Promise} Promise with model data
   */
  async getModelById(id) {
    try {
      const response = await axiosInstance.get(`/api/llm-models/${id}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Get models by provider
   * @param {string} provider - Provider name (openai, gemini, anthropic)
   * @returns {Promise} Promise with list of models
   */
  async getModelsByProvider(provider) {
    try {
      const response = await axiosInstance.get(`/api/llm-models/provider/${provider}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Create a new LLM model
   * @param {object} model - Model data
   * @returns {Promise} Promise with created model
   */
  async createModel(model) {
    try {
      const response = await axiosInstance.post('/api/llm-models', model);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Update an existing LLM model
   * @param {string} id - Model ID
   * @param {object} model - Updated model data
   * @returns {Promise} Promise with updated model
   */
  async updateModel(id, model) {
    try {
      const response = await axiosInstance.put(`/api/llm-models/${id}`, model);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Delete an LLM model
   * @param {string} id - Model ID
   * @returns {Promise} Promise indicating completion
   */
  async deleteModel(id) {
    try {
      const response = await axiosInstance.delete(`/api/llm-models/${id}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Initialize default models
   * @returns {Promise} Promise indicating completion
   */
  async initializeDefaultModels() {
    try {
      const response = await axiosInstance.post('/api/llm-models/initialize-defaults');
      return response.data;
    } catch (error) {
      throw error;
    }
  }
};

export default llmModelService;

