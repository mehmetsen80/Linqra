import axiosInstance from './axiosInstance';

const aiAssistantService = {
    getAllAssistants: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/ai-assistants/team/${teamId}`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching AI assistants:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch AI assistants'
            };
        }
    },

    getAssistant: async (assistantId) => {
        try {
            const response = await axiosInstance.get(`/api/ai-assistants/${assistantId}`);
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching AI assistant:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch AI assistant'
            };
        }
    },

    createAssistant: async (assistantData) => {
        try {
            const response = await axiosInstance.post('/api/ai-assistants', assistantData);
            return {
                success: true,
                data: response.data,
                message: 'AI Assistant created successfully'
            };
        } catch (error) {
            console.error('Error creating AI assistant:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create AI assistant'
            };
        }
    },

    updateAssistant: async (assistantId, assistantData) => {
        try {
            const response = await axiosInstance.put(`/api/ai-assistants/${assistantId}`, assistantData);
            return {
                success: true,
                data: response.data,
                message: 'AI Assistant updated successfully'
            };
        } catch (error) {
            console.error('Error updating AI assistant:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update AI assistant'
            };
        }
    },

    deleteAssistant: async (assistantId) => {
        try {
            const response = await axiosInstance.delete(`/api/ai-assistants/${assistantId}`);
            return {
                success: true,
                data: response.data,
                message: 'AI Assistant deleted successfully'
            };
        } catch (error) {
            console.error('Error deleting AI assistant:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to delete AI assistant'
            };
        }
    },

    updateAccessControl: async (assistantId, accessControl) => {
        try {
            const response = await axiosInstance.put(`/api/ai-assistants/${assistantId}/access-control`, accessControl);
            return {
                success: true,
                data: response.data,
                message: 'Access control updated successfully'
            };
        } catch (error) {
            console.error('Error updating access control:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update access control'
            };
        }
    },

    generatePublicApiKey: async (assistantId) => {
        try {
            const response = await axiosInstance.post(`/api/ai-assistants/${assistantId}/generate-api-key`);
            return {
                success: true,
                data: response.data,
                apiKey: response.data.apiKey,
                message: 'Public API key generated successfully'
            };
        } catch (error) {
            console.error('Error generating public API key:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to generate public API key'
            };
        }
    },

    getWidgetScript: async (assistantId) => {
        try {
            const response = await axiosInstance.get(`/api/ai-assistants/${assistantId}/widget-script`);
            return {
                success: true,
                data: response.data,
                scriptUrl: response.data.scriptUrl
            };
        } catch (error) {
            console.error('Error fetching widget script:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch widget script'
            };
        }
    },

    updateWidgetConfig: async (assistantId, widgetConfig) => {
        try {
            const response = await axiosInstance.put(`/api/ai-assistants/${assistantId}/widget-config`, widgetConfig);
            return {
                success: true,
                data: response.data,
                message: 'Widget configuration updated successfully'
            };
        } catch (error) {
            console.error('Error updating widget config:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update widget configuration'
            };
        }
    }
};

export default aiAssistantService;

