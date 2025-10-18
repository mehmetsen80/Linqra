import axiosInstance from './axiosInstance';

const llmCostService = {
  /**
   * Get LLM usage and cost statistics for a team
   * @param {string} teamId - The team ID
   * @param {string} fromDate - Start date (YYYY-MM-DD)
   * @param {string} toDate - End date (YYYY-MM-DD)
   * @returns {Promise} Promise with usage statistics
   */
  async getTeamLlmUsage(teamId, fromDate, toDate) {
    try {
      const params = new URLSearchParams();
      if (fromDate) params.append('fromDate', fromDate);
      if (toDate) params.append('toDate', toDate);

      const response = await axiosInstance.get(
        `/api/llm-costs/team/${teamId}/usage?${params.toString()}`
      );
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Get pricing snapshots for a specific month
   * @param {string} yearMonth - Year-month in format YYYY-MM
   * @returns {Promise} Promise with pricing snapshots
   */
  async getPricingSnapshots(yearMonth) {
    try {
      const response = await axiosInstance.get(`/api/llm-pricing/${yearMonth}`);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Save a new pricing snapshot
   * @param {object} snapshot - Pricing snapshot data
   * @returns {Promise} Promise with saved snapshot
   */
  async savePricingSnapshot(snapshot) {
    try {
      const response = await axiosInstance.post('/api/llm-pricing', snapshot);
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Initialize pricing for the current month
   * @returns {Promise} Promise indicating completion
   */
  async initializeCurrentMonthPricing() {
    try {
      const response = await axiosInstance.post('/api/llm-pricing/initialize-current-month');
      return response.data;
    } catch (error) {
      throw error;
    }
  },

  /**
   * Backfill historical pricing
   * @param {string} fromMonth - Start month (YYYY-MM)
   * @param {string} toMonth - End month (YYYY-MM)
   * @returns {Promise} Promise indicating completion
   */
  async backfillHistoricalPricing(fromMonth, toMonth) {
    try {
      const response = await axiosInstance.post(
        '/api/llm-pricing/backfill',
        null,
        {
          params: { fromMonth, toMonth }
        }
      );
      return response.data;
    } catch (error) {
      throw error;
    }
  }
};

export default llmCostService;

