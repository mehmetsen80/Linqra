import axiosInstance from './axiosInstance';

export const apiEndpointService = {
  async createEndpoint(routeIdentifier, swaggerJson) {
    try {
      const response = await axiosInstance.post('/api/endpoints', {
        routeIdentifier,
        swaggerJson,
        version: 1,
        createdAt: Date.now()
      });
      return response.data;
    } catch (error) {
      if (error.response?.data?.message) {
        console.error('Server error:', error.response.data.message);
        throw new Error(error.response.data.message);
      }
      console.error('Error creating endpoint:', error.message);
      throw error;
    }
  },

  async getEndpoint(id) {
    try {
      const response = await axiosInstance.get(`/api/endpoints/${id}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching endpoint:', error.response?.data?.message || error.message);
      throw error;
    }
  },

  async getEndpointsByRoute(routeIdentifier) {
    try {
      const response = await axiosInstance.get(`/api/endpoints/route/${routeIdentifier}`);
      return response.data;
    } catch (error) {
      console.error('Error getting endpoints by route:', error);
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw error;
    }
  },

  async getEndpointVersion(routeIdentifier, version) {
    try {
      const response = await axiosInstance.get(
        `/api/endpoints/route/${routeIdentifier}/version/${version}`
      );
      return response.data;
    } catch (error) {
      console.error('Error fetching endpoint version:', error.response?.data?.message || error.message);
      throw error;
    }
  },

  async updateEndpoint(id, endpointData) {
    try {
      // Ensure we're sending a clean, mutable copy of the data
      const cleanData = JSON.parse(JSON.stringify(endpointData));
      const response = await axiosInstance.put(`/api/endpoints/${id}`, cleanData);
      return response.data;
    } catch (error) {
      console.error('Error updating endpoint:', error.response?.data?.message || error.message);
      throw error;
    }
  },

  async deleteEndpoint(id) {
    try {
      await axiosInstance.delete(`/api/endpoints/${id}`);
      return true;
    } catch (error) {
      console.error('Error deleting endpoint:', error.response?.data?.message || error.message);
      throw error;
    }
  },

  async createNewVersion(id, endpointData) {
    try {
      // Send the endpoint data directly, without the extra wrapper
      const response = await axiosInstance.post(
        `/api/endpoints/${id}/versions`,
        {
          id: endpointData.id,
          version: endpointData.version,
          createdAt: endpointData.createdAt,
          updatedAt: endpointData.updatedAt,
          routeIdentifier: endpointData.routeIdentifier,
          swaggerJson: typeof endpointData.swaggerJson === 'string' 
            ? endpointData.swaggerJson 
            : JSON.stringify(endpointData.swaggerJson),
          name: endpointData.name,
          description: endpointData.description
        }
      );
      return response.data;
    } catch (error) {
      console.error('Error creating new endpoint version:', error.response?.data || error);
      throw error;
    }
  },

  async validateSwaggerJson(swaggerJson) {
    try {
      const response = await axiosInstance.post(
        '/api/endpoints/validate',
        swaggerJson
      );
      return response.data;
    } catch (error) {
      console.error('Error validating Swagger JSON:', error.response?.data?.message || error.message);
      throw error;
    }
  },

  async extractSwaggerInfo(swaggerJson) {
    try {
      // If swaggerJson is a string, parse it
      const swaggerData = typeof swaggerJson === 'string' ? JSON.parse(swaggerJson) : swaggerJson;
      
      // Make the API call to extract the information
      const response = await axiosInstance.post('/api/endpoints/extract-swagger', swaggerData);
      return response.data;
    } catch (error) {
      console.error('Error extracting Swagger info:', error);
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw error;
    }
  },

  async getEndpointVersions(routeIdentifier) {
    try {
      const response = await axiosInstance.get(`/api/endpoints/route/${routeIdentifier}/versions`);
      return response.data;
    } catch (error) {
      console.error('Error fetching endpoint versions:', error.response?.data?.message || error.message);
      throw error;
    }
  }
}; 