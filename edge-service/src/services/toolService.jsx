import axiosInstance from './axiosInstance';

const toolService = {
    getAllTools: async (teamId) => {
        try {
            const config = teamId ? { params: { teamId } } : {};
            const response = await axiosInstance.get('/api/tools', config);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    getTool: async (toolId, refresh = false) => {
        try {
            const config = refresh ? { params: { refresh: true } } : {};
            const response = await axiosInstance.get(`/api/tools/${toolId}`, config);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    registerTool: async (tool) => {
        try {
            const response = await axiosInstance.post('/api/tools', tool);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    updateTool: async (toolId, tool) => {
        try {
            const response = await axiosInstance.put(`/api/tools/${toolId}`, tool);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    deleteTool: async (toolId) => {
        try {
            await axiosInstance.delete(`/api/tools/${toolId}`);
            return { success: true };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    testToolConfig: async (toolId, toolDefinition, params) => {
        try {
            const response = await axiosInstance.post(`/api/tools/${toolId}/test`, {
                tool: toolDefinition,
                params: params
            });
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },


    getToolSkill: async (toolId) => {
        try {
            const response = await axiosInstance.get(`/api/tools/skills/${toolId}`);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    execute: async (toolId, params, refresh = false) => {
        try {
            const config = refresh ? { params: { refresh: true } } : {};
            const response = await axiosInstance.post(`/api/tools/${toolId}/execute`, params, config);
            return { success: true, data: response.data };
        } catch (error) {
            return { success: false, error: error.response?.data?.message || error.message };
        }
    },

    executeTool: async (toolId, params, refresh = false) => {
        return toolService.execute(toolId, params, refresh);
    }
};

export default toolService;
