import axiosInstance from './axiosInstance';

const vaultHealthService = {
  /**
   * Check vault health status
   * @returns {Promise} Promise with vault health status
   */
  checkVaultHealth: async () => {
    try {
      const response = await axiosInstance.get('/api/vault/health');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error checking vault health:', error);
      // If the request fails, assume vault is unhealthy
      return {
        success: false,
        data: {
          healthy: false,
          status: 'ERROR',
          message: error.response?.data?.message || error.message || 'Failed to check vault health',
          error: error.response?.data?.error || 'Unknown error'
        }
      };
    }
  }
};

export default vaultHealthService;

