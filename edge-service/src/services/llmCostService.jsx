import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || 'https://localhost:7777';

const llmCostService = {
  /**
   * Get LLM usage and cost statistics for a team
   * @param {string} teamId - The team ID
   * @param {string} fromDate - Start date (YYYY-MM-DD)
   * @param {string} toDate - End date (YYYY-MM-DD)
   * @returns {Promise} Promise with usage statistics
   */
  async getTeamLlmUsage(teamId, fromDate, toDate) {
    const params = new URLSearchParams();
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);

    const response = await axios.get(
      `${API_BASE_URL}/api/llm-costs/team/${teamId}/usage?${params.toString()}`
    );
    return response.data;
  },

  /**
   * Get pricing snapshots for a specific month
   * @param {string} yearMonth - Year-month in format YYYY-MM
   * @returns {Promise} Promise with pricing snapshots
   */
  async getPricingSnapshots(yearMonth) {
    const response = await axios.get(
      `${API_BASE_URL}/api/llm-pricing/${yearMonth}`
    );
    return response.data;
  },

  /**
   * Save a new pricing snapshot
   * @param {object} snapshot - Pricing snapshot data
   * @returns {Promise} Promise with saved snapshot
   */
  async savePricingSnapshot(snapshot) {
    const response = await axios.post(
      `${API_BASE_URL}/api/llm-pricing`,
      snapshot
    );
    return response.data;
  },

  /**
   * Initialize pricing for the current month
   * @returns {Promise} Promise indicating completion
   */
  async initializeCurrentMonthPricing() {
    const response = await axios.post(
      `${API_BASE_URL}/api/llm-pricing/initialize-current-month`
    );
    return response.data;
  },

  /**
   * Backfill historical pricing
   * @param {string} fromMonth - Start month (YYYY-MM)
   * @param {string} toMonth - End month (YYYY-MM)
   * @returns {Promise} Promise indicating completion
   */
  async backfillHistoricalPricing(fromMonth, toMonth) {
    const response = await axios.post(
      `${API_BASE_URL}/api/llm-pricing/backfill`,
      null,
      {
        params: { fromMonth, toMonth }
      }
    );
    return response.data;
  }
};

export default llmCostService;

