import axiosInstance from './axiosInstance';

const agentService = {
    createAgent: async (agentData) => {
        console.log('Creating agent:', agentData);
        try {
            const response = await axiosInstance.post('/api/agents', agentData, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent created successfully'
            };
        } catch (error) {
            console.error('Error creating agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to create agent'
            };
        }
    },

    getAgentsByTeam: async (teamId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/team/${teamId}`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agents:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agents'
            };
        }
    },

    getAgent: async (teamId, agentId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/team/${teamId}/${agentId}`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent'
            };
        }
    },

    enableAgent: async (agentId, teamId) => {
        console.log('Enabling agent:', agentId);
        try {
            const response = await axiosInstance.post(`/api/agents/${agentId}/enable`, null, {
                params: { teamId },
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent enabled successfully'
            };
        } catch (error) {
            console.error('Error enabling agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to enable agent'
            };
        }
    },

    disableAgent: async (agentId, teamId) => {
        console.log('Disabling agent:', agentId);
        try {
            const response = await axiosInstance.post(`/api/agents/${agentId}/disable`, null, {
                params: { teamId },
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent disabled successfully'
            };
        } catch (error) {
            console.error('Error disabling agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to disable agent'
            };
        }
    },

    updateAgent: async (agentId, agentUpdates) => {
        console.log('Updating agent:', agentId);
        try {
            const response = await axiosInstance.put(`/api/agents/${agentId}`, agentUpdates, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent updated successfully'
            };
        } catch (error) {
            console.error('Error updating agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to update agent'
            };
        }
    },

    deleteAgent: async (agentId, teamId) => {
        console.log('Deleting agent:', agentId);
        try {
            const response = await axiosInstance.delete(`/api/agents/${agentId}`, {
                params: { teamId },
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data,
                message: 'Agent deleted successfully'
            };
        } catch (error) {
            console.error('Error deleting agent:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to delete agent'
            };
        }
    },

    getTasksByAgent: async (agentId) => {
        try {
            const response = await axiosInstance.get(`/api/agents/${agentId}/tasks`, {
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json',
                    'X-API-Key': import.meta.env.VITE_API_KEY,
                    'X-API-Key-Name': import.meta.env.VITE_API_KEY_NAME
                }
            });
            return {
                success: true,
                data: response.data
            };
        } catch (error) {
            console.error('Error fetching agent tasks:', error);
            return {
                success: false,
                error: error.response?.data?.message || 'Failed to fetch agent tasks'
            };
        }
    }
};

export default agentService;

