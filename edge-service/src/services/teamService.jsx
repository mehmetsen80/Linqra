import axiosInstance from './axiosInstance';

export const teamService = {
  // Get all teams
  getAllTeams: async () => {
    try {
      const response = await axiosInstance.get('/api/teams');
      return {
        success: true,
        data: response.data  // The API is returning the array directly
      };
    } catch (error) {
      console.error('Error fetching all teams:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch all teams'
      };
    }
  },

  // Create new team
  createTeam: async (teamData) => {
    try {
      const response = await axiosInstance.post('/api/teams', teamData);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Update team
  updateTeam: async (teamId, teamData) => {
    try {
      const response = await axiosInstance.put(`/api/teams/${teamId}`, {
        name: teamData.name,
        description: teamData.description,
        organizationId: teamData.organizationId
      });
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Delete team
  deleteTeam: async (teamId) => {
    try {
      const response = await axiosInstance.delete(`/api/teams/${teamId}`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Get team members
  getTeamMembers: async (teamId) => {
    try {
      const response = await axiosInstance.get(`/api/teams/${teamId}/members`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Add team member
  addTeamMember: async (teamId, memberData) => {
    try {
      const response = await axiosInstance.post(
        `/api/teams/${teamId}/members`,
        null,
        {
          params: {
            username: memberData.username,
            role: memberData.role
          }
        }
      );
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Remove team member
  removeTeamMember: async (teamId, userId) => {
    try {
      const response = await axiosInstance.delete(`/api/teams/${teamId}/members/${userId}`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Add team route
  addTeamRoute: async (teamId, routeId, permissions) => {
    try {
      const response = await axiosInstance.post(
        `/api/teams/route-assignment`,
        { teamId, routeId, permissions }
      );
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Remove team route
  removeTeamRoute: async (teamId, routeId) => {
    try {
      const response = await axiosInstance.delete(`/api/teams/${teamId}/routes/${routeId}`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Activate team
  activateTeam: async (teamId) => {
    try {
      const response = await axiosInstance.put(`/api/teams/${teamId}/activate`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Deactivate team
  deactivateTeam: async (teamId) => {
    try {
      const response = await axiosInstance.put(`/api/teams/${teamId}/deactivate`);
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Get all available routes
  getAllRoutes: async () => {
    try {
      const response = await axiosInstance.get('/api/routes');
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Get team routes
  getTeamRoutes: async (teamId) => {
    try {
      const response = await axiosInstance.get(`/api/teams/${teamId}/routes`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching team routes:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch team routes'
      };
    }
  },

  getUserTeams: async () => {
    try {
      const response = await axiosInstance.get('/api/teams/user/current');
      return { data: response.data };
    } catch (error) {
      return { error: error.message };
    }
  },

  // Add this new method to teamService
  getAllTeamRoutes: async () => {
    try {
      const response = await axiosInstance.get('/api/teams/routes/all');
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching all team routes:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch all team routes'
      };
    }
  },

  createApiKey: async (apiKeyData) => {
    try {
      const response = await axiosInstance.post('/api/keys', {
        name: apiKeyData.name,
        teamId: apiKeyData.teamId,
        expiresInDays: apiKeyData.expiresInDays  // This will be null for infinite
      });
      return { data: response.data };
    } catch (error) {
      return { error: error.response?.data?.message || error.message };
    }
  },

  getTeamApiKeys: async (teamId) => {
    try {
      console.log('Making API call for teamId:', teamId);
      const response = await axiosInstance.get(`/api/keys`, {
        params: { teamId }
      });
      return { data: response.data };
    } catch (error) {
      return { error: error.response?.data?.message || error.message };
    }
  },

  revokeApiKey: async (keyId) => {
    try {
      await axiosInstance.post(`/api/keys/${keyId}/revoke`);
      return { success: true };
    } catch (error) {
      return { error: error.response?.data?.message || error.message };
    }
  },

  removeApiKey: async (keyId) => {
    try {
      await axiosInstance.delete(`/api/keys/${keyId}`);
      return { success: true };
    } catch (error) {
      return { error: error.response?.data?.message || error.message };
    }
  },

  // Get team by ID
  getTeam: async (teamId) => {
    try {
      const response = await axiosInstance.get(`/api/teams/${teamId}`);
      return {
        success: true,
        data: response.data
      };
    } catch (error) {
      console.error('Error fetching team:', error);
      return {
        success: false,
        error: error.response?.data?.message || 'Failed to fetch team'
      };
    }
  }
}; 