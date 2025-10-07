import axiosInstance from './axiosInstance';

export const apiRouteService = {
  async getAllRoutes(teamId) {
    try {
      const url = teamId 
        ? `/api/routes?teamId=${teamId}`
        : '/api/routes';
        
      const response = await axiosInstance.get(url);
      return response.data;
    } catch (error) {
      console.error('Error fetching routes:', error);
      throw error;
    }
  },

  async getRouteById(routeId) {
    try {
      const response = await axiosInstance.get(`/api/routes/${routeId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching route by ID:', error);
      throw error;
    }
  },

  async getRouteByIdentifier(routeIdentifier) {
    try {
      const response = await axiosInstance.get(`/api/routes/identifier/${routeIdentifier}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching route by identifier:', error);
      throw error;
    }
  },

  async updateRoute(routeIdentifier, routeData) {
    try {
      // Ensure we're sending a clean, mutable copy of the data
      const cleanData = JSON.parse(JSON.stringify(routeData));
      const response = await axiosInstance.put(`/api/routes/${routeIdentifier}`, cleanData);
      return response.data;
    } catch (error) {
      console.error('Error updating route:', error);
      throw error;
    }
  },

  async deleteRoute(routeIdentifier) {
    try {
      console.log('deleting route', routeIdentifier);
      await axiosInstance.delete(`/api/routes/${routeIdentifier}`);
      return true;
    } catch (error) {
      console.error('Error deleting route:', error);
      throw error;
    }
  },

  async createRoute(routeData) {
    try {
      const response = await axiosInstance.post('/api/routes', routeData);
      return response.data;
    } catch (error) {
      console.error('Error creating route:', error);
      throw error;
    }
  },

  getRouteVersions: async (routeIdentifier) => {
    const response = await axiosInstance.get(`/api/routes/identifier/${routeIdentifier}/versions`);
    return response.data;
  },

  getRouteVersion: async (routeIdentifier, version) => {
    const response = await axiosInstance.get(`/api/routes/identifier/${routeIdentifier}/versions/${version}`);
    return response.data;
  },

  getVersionMetadata: async (routeIdentifier) => {
    console.log('getting version metadata', routeIdentifier);
    const response = await axiosInstance.get(`/api/routes/identifier/${routeIdentifier}/metadata`);
    return response.data;
  },

  compareVersions: async (routeIdentifier, version1, version2) => {
    const response = await axiosInstance.get(
      `/api/routes/identifier/${routeIdentifier}/compare?version1=${version1}&version2=${version2}`
    );
    return response.data;
  },

  getServiceInteractionsSummary: async (serviceName, startDate = null, endDate = null) => {
    try {
      let url = `/api/metrics/service-interactions/${serviceName}/summary`;
      const params = [];
      
      if (startDate) {
        params.push(`startDate=${encodeURIComponent(startDate)}`);
      }
      if (endDate) {
        params.push(`endDate=${encodeURIComponent(endDate)}`);
      }
      
      if (params.length > 0) {
        url += `?${params.join('&')}`;
      }
      
      const response = await axiosInstance.get(url);
      return response.data;
    } catch (error) {
      console.error('Error fetching service interactions summary:', error);
      throw error;
    }
  },

  getTopEndpointsByService: async (serviceName, startDate = null, endDate = null, limit = 10) => {
    try {
      let url = `/api/metrics/top-endpoints/${serviceName}`;
      const params = [];
      
      if (startDate) {
        params.push(`startDate=${encodeURIComponent(startDate)}`);
      }
      if (endDate) {
        params.push(`endDate=${encodeURIComponent(endDate)}`);
      }
      if (limit && limit !== 10) {
        params.push(`limit=${limit}`);
      }
      
      if (params.length > 0) {
        url += `?${params.join('&')}`;
      }
      
      const response = await axiosInstance.get(url);
      return response.data;
    } catch (error) {
      console.error('Error fetching top endpoints by service:', error);
      throw error;
    }
  }
}; 