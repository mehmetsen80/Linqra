import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'https://localhost:7777';

const llmModelService = {
  /**
   * Get all LLM models
   * @param {boolean} activeOnly - If true, only return active models
   * @returns {Promise} Promise with list of LLM models
   */
  async getAllModels(activeOnly = false) {
    const params = activeOnly ? '?active=true' : '';
    const response = await axios.get(`${API_BASE_URL}/api/llm-models${params}`);
    return response.data;
  },

  /**
   * Get model by ID
   * @param {string} id - Model ID
   * @returns {Promise} Promise with model data
   */
  async getModelById(id) {
    const response = await axios.get(`${API_BASE_URL}/api/llm-models/${id}`);
    return response.data;
  },

  /**
   * Get models by provider
   * @param {string} provider - Provider name (openai, gemini, anthropic)
   * @returns {Promise} Promise with list of models
   */
  async getModelsByProvider(provider) {
    const response = await axios.get(`${API_BASE_URL}/api/llm-models/provider/${provider}`);
    return response.data;
  },

  /**
   * Create a new LLM model
   * @param {object} model - Model data
   * @returns {Promise} Promise with created model
   */
  async createModel(model) {
    const response = await axios.post(`${API_BASE_URL}/api/llm-models`, model);
    return response.data;
  },

  /**
   * Update an existing LLM model
   * @param {string} id - Model ID
   * @param {object} model - Updated model data
   * @returns {Promise} Promise with updated model
   */
  async updateModel(id, model) {
    const response = await axios.put(`${API_BASE_URL}/api/llm-models/${id}`, model);
    return response.data;
  },

  /**
   * Delete an LLM model
   * @param {string} id - Model ID
   * @returns {Promise} Promise indicating completion
   */
  async deleteModel(id) {
    const response = await axios.delete(`${API_BASE_URL}/api/llm-models/${id}`);
    return response.data;
  },

  /**
   * Initialize default models
   * @returns {Promise} Promise indicating completion
   */
  async initializeDefaultModels() {
    const response = await axios.post(`${API_BASE_URL}/api/llm-models/initialize-defaults`);
    return response.data;
  }
};

export default llmModelService;

