import axiosInstance from './axiosInstance';

export const linqToolService = {
  // Get LinqTool configuration for a team
  getTeamConfiguration: async (teamId) => {
    try {
      const response = await axiosInstance.get(`/api/linq-tools/team/${teamId}`, {
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('Error fetching team configuration:', error);
      throw error;
    }
  },

  // Get specific LinqTool configuration for a team and target
  getToolConfiguration: async (teamId, target) => {
    try {
      const response = await axiosInstance.get(`/api/linq-tools/team/${teamId}/target/${target}`, {
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('Error fetching tool configuration:', error);
      throw error;
    }
  },

  // Save LinqTool configuration
  saveConfiguration: async (configuration) => {
    try {
      const response = await axiosInstance.post('/api/linq-tools', configuration, {
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('Error saving configuration:', error);
      throw error;
    }
  }
};
