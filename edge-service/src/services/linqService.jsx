import axiosInstance from './axiosInstance';

export const linqService = {
  convertToLinq: async (requestData) => {
    try {
      const response = await axiosInstance.post('/linq/convert', requestData, {
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('Error in convertToLinq:', error);
      throw error;
    }
  },

  // Helper function to determine Linq action from HTTP method
  getLinqAction: (method) => {
    switch (method.toLowerCase()) {
      case 'get': return 'fetch';
      case 'post': return 'create';
      case 'put': return 'update';
      case 'delete': return 'delete';
      default: return 'fetch';
    }
  },

  // Helper function to format Linq request
  formatLinqRequest: (routeIdentifier, endpoint) => {
    // Extract path parameters from the endpoint path
    const pathParams = {};
    const pathParamRegex = /{([^}]+)}/g;
    let match;
    while ((match = pathParamRegex.exec(endpoint.path)) !== null) {
      pathParams[match[1]] = '';  // Empty string as placeholder for actual values
    }

    return {
      link: {
        target: routeIdentifier,
        action: linqService.getLinqAction(endpoint.method)
      },
      query: {
        intent: endpoint.path.replace(`/${routeIdentifier}/`, ''),
        params: pathParams,
        payload: endpoint.requestBody ? {} : undefined  // Empty object as placeholder for actual payload
      }
    };
  },

  // Helper function to generate example values based on type
  getExampleValue: (type) => {
    switch (type?.toLowerCase()) {
      case 'integer':
      case 'int64':
        return 1;
      case 'number':
      case 'double':
        return 99.99;
      case 'string':
        return 'example';
      case 'boolean':
        return true;
      default:
        return 'example';
    }
  },

  // Helper function to create example payload
  createExamplePayload: (schema) => {
    if (!schema) return null;
    
    const example = {};
    if (schema.properties) {
      Object.entries(schema.properties).forEach(([key, prop]) => {
        example[key] = linqService.getExampleValue(prop.type);
      });
    }
    return example;
  },

  // Helper function to format example response
  formatExampleResponse: (routeIdentifier, endpoint) => {
    let exampleResult;
    
    // Get example response from 200 or 201 schema
    const successResponse = endpoint.responses['200'] || endpoint.responses['201'];
    if (successResponse?.content?.['application/json']?.schema) {
      const schema = successResponse.content['application/json'].schema;
      exampleResult = linqService.createExamplePayload(schema);
    }

    return {
      result: exampleResult,
      metadata: {
        source: routeIdentifier,
        status: "success",
        team: "YOUR_TEAM_ID",
        cacheHit: false
      }
    };
  }
}; 